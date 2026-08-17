package com.uniqa.crmpoc.email;

import com.icegreen.greenmail.util.GreenMail;
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
 * EmailSource backed by the embedded GreenMail IMAP server. Reads unseen
 * messages and converts them to RawEmail; messages are only marked as seen
 * once the caller confirms (via acknowledge()) that they were durably
 * processed downstream, so a failure after fetch doesn't lose the email -
 * the same contract a real IMAP/Graph source would follow.
 */
@Component
@ConditionalOnProperty(name = "email.source", havingValue = "greenmail", matchIfMissing = true)
@Slf4j
public class GreenMailEmailSource implements EmailSource {

    private final GreenMail greenMail;
    private final String testAccount;
    private final String testPassword;
    private final int imapPort;

    public GreenMailEmailSource(GreenMail greenMail,
                                 @Value("${email.greenmail.test-account}") String testAccount,
                                 @Value("${email.greenmail.test-password}") String testPassword,
                                 @Value("${email.greenmail.imap-port}") int imapPort) {
        this.greenMail = greenMail;
        this.testAccount = testAccount;
        this.testPassword = testPassword;
        this.imapPort = imapPort;
    }

    @Override
    public List<RawEmail> fetchNewEmails() {
        List<RawEmail> result = new ArrayList<>();
        try {
            Store store = connect();
            Folder inbox = store.getFolder("INBOX");
            try {
                inbox.open(Folder.READ_ONLY);
                Message[] unseen = inbox.search(new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false));
                for (Message message : unseen) {
                    try {
                        result.add(MimeMessageMapper.toRawEmail(message));
                    } catch (Exception e) {
                        log.error("Failed to parse a fetched message, leaving it unseen for retry", e);
                    }
                }
            } finally {
                inbox.close(false);
                store.close();
            }
        } catch (Exception e) {
            log.error("Failed to fetch emails from test mailbox", e);
        }
        return result;
    }

    @Override
    public void acknowledge(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return;
        }
        Set<String> pending = new HashSet<>(messageIds);
        try {
            Store store = connect();
            Folder inbox = store.getFolder("INBOX");
            try {
                inbox.open(Folder.READ_WRITE);
                for (Message message : inbox.getMessages()) {
                    try {
                        if (pending.contains(MimeMessageMapper.resolveMessageId(message))) {
                            message.setFlag(Flags.Flag.SEEN, true);
                        }
                    } catch (Exception e) {
                        log.error("Failed to mark a message as seen; it may be reprocessed on next poll", e);
                    }
                }
            } finally {
                inbox.close(true);
                store.close();
            }
        } catch (Exception e) {
            log.error("Failed to acknowledge {} processed email(s); they may be reprocessed on next poll",
                    messageIds.size(), e);
        }
    }

    private Store connect() throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect("localhost", imapPort, testAccount, testPassword);
        return store;
    }
}
