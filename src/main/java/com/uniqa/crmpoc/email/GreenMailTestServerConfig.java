package com.uniqa.crmpoc.email;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Starts an embedded IMAP+SMTP server (GreenMail) and seeds it with sample
 * UNIQA-style customer emails, so the whole demo runs with zero dependency
 * on real UNIQA mailboxes or the public internet.
 *
 * Swap email.source to "imap" and point EmailSource at a real server once
 * mailbox access is granted - the rest of the app is unaffected.
 */
@Configuration
@ConditionalOnProperty(name = "email.source", havingValue = "greenmail", matchIfMissing = true)
@Slf4j
public class GreenMailTestServerConfig {

    @Value("${email.greenmail.imap-port}")
    private int imapPort;

    @Value("${email.greenmail.smtp-port}")
    private int smtpPort;

    @Value("${email.greenmail.test-account}")
    private String testAccount;

    @Value("${email.greenmail.test-password}")
    private String testPassword;

    private GreenMail greenMail;
    private ServerSetup smtpSetup;

    @Bean
    public GreenMail greenMailServer() {
        ServerSetup imap = new ServerSetup(imapPort, null, ServerSetup.PROTOCOL_IMAP);
        smtpSetup = new ServerSetup(smtpPort, null, ServerSetup.PROTOCOL_SMTP);
        greenMail = new GreenMail(new ServerSetup[]{imap, smtpSetup});
        greenMail.start();
        for (DemoMailAccounts.Def account : DemoMailAccounts.ALL) {
            greenMail.setUser(account.address(), account.address(), testPassword);
        }
        log.info("GreenMail test mailbox started: IMAP {} / SMTP {} / {} account(s), primary {}",
                imapPort, smtpPort, DemoMailAccounts.ALL.size(), testAccount);
        seedSampleEmails();
        return greenMail;
    }

    @PreDestroy
    public void stop() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    /**
     * Sends a handful of realistic sample emails into the test inbox via SMTP,
     * covering every target category plus a couple of ambiguous ones - so the
     * demo has something meaningful for the rule engine + NLP to chew on.
     */
    private void seedSampleEmails() {
        send("Complaint about my car insurance claim",
                "I am extremely disappointed and frustrated with how my claim CLM-88213 " +
                "has been handled. It has been six weeks and nobody has responded to my calls. " +
                "This is unacceptable and I want this resolved immediately.",
                "angry.customer@example.com");

        send("New claim submission - water damage",
                "Hello, I would like to submit a new claim for water damage to my apartment " +
                "following the storm last week. Please advise on the required documents. " +
                "Policy number PL-44210.",
                "maria.novak@example.com");

        send("Question about updating my policy address",
                "Hi, I recently moved and need to update the address on my home insurance policy " +
                "PL-99213. Could you let me know how to proceed?",
                "jan.kovac@example.com");

        send("General question about coverage",
                "Good afternoon, I'm considering adding travel insurance to my existing policy " +
                "and wanted to ask what regions are covered under the standard plan.",
                "info.seeker@example.com");

        send("Terrible experience with your call center",
                "I called three times this week about claim CLM-77012 and every time I was put " +
                "on hold for over 40 minutes and then disconnected. I am considering cancelling " +
                "all my policies with UNIQA.",
                "unhappy.client@example.com");

        send("Claim status inquiry",
                "Could you please provide an update on the status of claim CLM-55031 submitted " +
                "two weeks ago? Thank you.",
                "petra.svoboda@example.com");

        sendWithAttachment("New claim submission - fire damage, photos attached",
                "Hello, please find attached photos of the fire damage to my kitchen for claim " +
                "CLM-91004. Policy number PL-30871. Let me know if you need anything else.",
                "tomas.dvorak@example.com",
                "kitchen_damage.jpg");

        // A couple of emails on other mailboxes, so multi-account intake is visible
        // out of the box rather than only after someone emails a non-primary address.
        send("Terrible support, considering cancelling",
                "I am extremely frustrated and disappointed with the support I received. " +
                "This has been an unacceptable experience overall.",
                "complaints@uniqa-poc.local", "lena.gruber@example.com");

        send("Question about adding a driver to my policy",
                "Hi, I'd like to update my policy PL-51234 to add my partner as a second driver. " +
                "Could you let me know what's needed?",
                "policy-changes@uniqa-poc.local", "peter.wagner@example.com");

        greenMail.waitForIncomingEmail(9);
        log.info("Seeded 9 sample emails across {} mailbox(es)", DemoMailAccounts.ALL.size());
    }

    private void send(String subject, String body, String from) {
        send(subject, body, testAccount, from);
    }

    private void send(String subject, String body, String to, String from) {
        GreenMailUtil.sendTextEmailTest(to, from, subject, body);
    }

    private void sendWithAttachment(String subject, String body, String from, String filename) {
        byte[] attachment = "fake image bytes for POC demo purposes".getBytes();
        GreenMailUtil.sendAttachmentEmail(testAccount, from, subject, body,
                attachment, "image/jpeg", filename, "Attached photo", smtpSetup);
    }
}
