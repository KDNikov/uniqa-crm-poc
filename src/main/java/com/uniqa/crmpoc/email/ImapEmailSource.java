package com.uniqa.crmpoc.email;

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
 * EmailSource backed by a real IMAP mailbox (e.g. Microsoft 365 / Exchange
 * Online). Mirrors GreenMailEmailSource's fetch/acknowledge contract: a
 * message is only marked Seen once the caller confirms it was durably
 * processed, so a crash between fetch and persist doesn't lose the email.
 *
 * Activated via email.source=imap (see application.yml / IMAP_* env vars).
 */
@Component
@ConditionalOnProperty(name = "email.source", havingValue = "imap")
@Slf4j
public class ImapEmailSource implements EmailSource {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public ImapEmailSource(@Value("${email.imap.host}") String host,
                            @Value("${email.imap.port}") int port,
                            @Value("${email.imap.username}") String username,
                            @Value("${email.imap.password}") String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        log.info("ImapEmailSource configured for {}@{}:{}", username, host, port);
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
            log.error("Failed to fetch emails from IMAP mailbox {}@{}", username, host, e);
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
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.host", host);
        props.setProperty("mail.imaps.port", String.valueOf(port));
        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(host, port, username, password);
        return store;
    }
}
