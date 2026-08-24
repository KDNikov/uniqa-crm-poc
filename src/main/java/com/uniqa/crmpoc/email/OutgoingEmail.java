package com.uniqa.crmpoc.email;

import java.util.List;

/** An email composed in the CRM (Reply/Reply All/Forward) waiting to be sent. */
public record OutgoingEmail(
        /** Which configured, send-capable MailAccount to send as. */
        String fromAddress,
        List<String> to,
        List<String> cc,
        String subject,
        String body,
        /** Message-ID of the email being replied to/forwarded, for threading headers. Null for a fresh compose. */
        String inReplyTo
) {}
