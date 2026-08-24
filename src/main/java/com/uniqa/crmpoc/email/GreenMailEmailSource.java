package com.uniqa.crmpoc.email;

import com.icegreen.greenmail.util.GreenMail;
import com.uniqa.crmpoc.domain.MailAccount;
import com.uniqa.crmpoc.repository.MailAccountRepository;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * EmailSource backed by the embedded GreenMail IMAP server. Polls every
 * configured MailAccount (the demo's stand-in for the ~20-30 real UNIQA
 * department/branch inboxes) rather than a single mailbox, so intake scales
 * the same way it would against N real IMAP logins. Messages are only
 * marked as seen once the caller confirms (via acknowledge()) that they
 * were durably processed downstream, so a failure after fetch doesn't lose
 * the email - the same contract a real IMAP/Graph source would follow.
 */
@Component
@ConditionalOnProperty(name = "email.source", havingValue = "greenmail", matchIfMissing = true)
@Slf4j
public class GreenMailEmailSource implements EmailSource {

    private final GreenMail greenMail;
    private final MailAccountRepository mailAccountRepository;
    private final String testPassword;
    private final int imapPort;

    public GreenMailEmailSource(GreenMail greenMail,
                                 MailAccountRepository mailAccountRepository,
                                 @Value("${email.greenmail.test-password}") String testPassword,
                                 @Value("${email.greenmail.imap-port}") int imapPort) {
        this.greenMail = greenMail;
        this.mailAccountRepository = mailAccountRepository;
        this.testPassword = testPassword;
        this.imapPort = imapPort;
    }

    @Override
    public List<RawEmail> fetchNewEmails() {
        List<RawEmail> result = new ArrayList<>();
        for (MailAccount account : mailAccountRepository.findAll()) {
            try {
                Store store = connect(account.getAddress());
                Folder inbox = store.getFolder("INBOX");
                try {
                    inbox.open(Folder.READ_ONLY);
                    Message[] unseen = inbox.search(new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false));
                    for (Message message : unseen) {
                        try {
                            result.add(MimeMessageMapper.toRawEmail(message, account.getAddress()));
                        } catch (Exception e) {
                            log.error("Failed to parse a fetched message on {}, leaving it unseen for retry",
                                    account.getAddress(), e);
                        }
                    }
                } finally {
                    inbox.close(false);
                    store.close();
                }
            } catch (Exception e) {
                log.error("Failed to fetch emails from mailbox {}", account.getAddress(), e);
            }
        }
        return result;
    }

    @Override
    public void acknowledge(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return;
        }
        Set<String> pending = new HashSet<>(messageIds);
        for (MailAccount account : mailAccountRepository.findAll()) {
            try {
                Store store = connect(account.getAddress());
                Folder inbox = store.getFolder("INBOX");
                try {
                    inbox.open(Folder.READ_WRITE);
                    for (Message message : inbox.getMessages()) {
                        try {
                            if (pending.contains(MimeMessageMapper.resolveMessageId(message, account.getAddress()))) {
                                message.setFlag(Flags.Flag.SEEN, true);
                            }
                        } catch (Exception e) {
                            log.error("Failed to mark a message as seen on {}; it may be reprocessed on next poll",
                                    account.getAddress(), e);
                        }
                    }
                } finally {
                    inbox.close(true);
                    store.close();
                }
            } catch (Exception e) {
                log.error("Failed to acknowledge processed email(s) on {}; they may be reprocessed on next poll",
                        account.getAddress(), e);
            }
        }
    }

    private Store connect(String address) throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect("localhost", imapPort, address, testPassword);
        return store;
    }
}
