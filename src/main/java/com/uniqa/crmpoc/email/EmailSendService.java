package com.uniqa.crmpoc.email;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Sends Reply/Reply All/Forward emails via the embedded GreenMail SMTP server -
 * the same test mailbox EmailIntakeScheduler reads from, so a "sent" email is
 * really delivered, not just simulated. A real deployment would swap this for
 * Graph API / real SMTP the same way ImapEmailSource stands in for GreenMailEmailSource.
 *
 * Unconditional (unlike GreenMailEmailSource/ImapEmailSource) because EmailController
 * always needs a sender bean to wire; under email.source=imap the GreenMail SMTP server
 * this points at isn't running, so send() would only fail lazily at call time - consistent
 * with the rest of the app, where the imap path is otherwise unconfigured and untested.
 */
@Service
@Slf4j
public class EmailSendService {

    private final int smtpPort;
    private final String fromAddress;

    public EmailSendService(@Value("${email.greenmail.smtp-port}") int smtpPort,
                             @Value("${email.greenmail.test-account}") String fromAddress) {
        this.smtpPort = smtpPort;
        this.fromAddress = fromAddress;
    }

    public void send(OutgoingEmail outgoing) {
        try {
            Properties props = new Properties();
            props.setProperty("mail.smtp.host", "localhost");
            props.setProperty("mail.smtp.port", String.valueOf(smtpPort));
            Session session = Session.getInstance(props);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, toAddresses(outgoing.to()));
            if (outgoing.cc() != null && !outgoing.cc().isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, toAddresses(outgoing.cc()));
            }
            message.setSubject(outgoing.subject());
            message.setText(outgoing.body());
            if (outgoing.inReplyTo() != null) {
                message.setHeader("In-Reply-To", outgoing.inReplyTo());
                message.setHeader("References", outgoing.inReplyTo());
            }

            Transport.send(message);
            log.info("Sent email '{}' to {}", outgoing.subject(), outgoing.to());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private InternetAddress[] toAddresses(java.util.List<String> raw) throws jakarta.mail.internet.AddressException {
        return InternetAddress.parse(String.join(",", raw));
    }
}
