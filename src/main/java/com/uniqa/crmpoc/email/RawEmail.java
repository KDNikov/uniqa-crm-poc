package com.uniqa.crmpoc.email;

import java.time.Instant;
import java.util.List;

/**
 * Transport-agnostic representation of a fetched email. Every EmailSource
 * implementation (GreenMail today, Graph API / real IMAP later) produces
 * this same shape, so nothing downstream cares where the email came from.
 */
public record RawEmail(
        String messageId,
        String fromAddress,
        List<String> toAddresses,
        List<String> ccAddresses,
        String replyTo,
        String inReplyTo,
        String referencesHeader,
        String rawHeaders,
        String subject,
        String body,
        Instant receivedAt,
        boolean hasAttachment,
        List<RawAttachment> attachments
) {
    public record RawAttachment(String filename, long sizeBytes, String contentType) {}
}
