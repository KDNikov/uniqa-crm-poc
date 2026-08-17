package com.uniqa.crmpoc.email;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Shared jakarta.mail Message -> RawEmail conversion, used by every
 * EmailSource so metadata extraction (recipients, threading headers, raw
 * headers, attachments) doesn't drift between transports.
 */
final class MimeMessageMapper {

    private MimeMessageMapper() {
    }

    static RawEmail toRawEmail(Message message) throws Exception {
        String from = addresses(message.getFrom()).stream().findFirst().orElse("unknown@unknown");
        List<String> to = addresses(message.getRecipients(Message.RecipientType.TO));
        List<String> cc = addresses(message.getRecipients(Message.RecipientType.CC));
        String replyTo = String.join(", ", addresses(message.getReplyTo()));

        StringBuilder text = new StringBuilder();
        List<RawEmail.RawAttachment> attachments = new ArrayList<>();
        collectParts(message.getContent(), text, attachments);

        return new RawEmail(
                resolveMessageId(message),
                from,
                to,
                cc,
                replyTo.isEmpty() ? null : replyTo,
                firstHeader(message, "In-Reply-To"),
                firstHeader(message, "References"),
                dumpHeaders(message),
                message.getSubject(),
                text.toString(),
                message.getSentDate() != null ? message.getSentDate().toInstant() : Instant.now(),
                !attachments.isEmpty(),
                attachments
        );
    }

    /**
     * Prefers the Message-ID header. Real messages always have one, but as a
     * fallback we key off the message's IMAP UID (stable across sessions until
     * the message is expunged) rather than from+subject+sentDate, which can
     * collide for distinct messages sharing a sender/subject/timestamp.
     */
    static String resolveMessageId(Message message) throws Exception {
        String header = firstHeader(message, "Message-ID");
        if (header != null) {
            return header;
        }
        String from = addresses(message.getFrom()).stream().findFirst().orElse("unknown@unknown");
        long uid = message.getFolder() instanceof UIDFolder uidFolder
                ? uidFolder.getUID(message)
                : message.getMessageNumber();
        return from + "-uid" + uid;
    }

    private static List<String> addresses(Address[] addresses) {
        List<String> result = new ArrayList<>();
        if (addresses == null) {
            return result;
        }
        for (Address address : addresses) {
            result.add(address instanceof InternetAddress internetAddress
                    ? internetAddress.getAddress()
                    : address.toString());
        }
        return result;
    }

    private static String firstHeader(Message message, String name) throws Exception {
        String[] values = message.getHeader(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    private static String dumpHeaders(Message message) throws Exception {
        StringBuilder dump = new StringBuilder();
        Enumeration<?> headers = message.getAllHeaders();
        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            dump.append(header.getName()).append(": ").append(header.getValue()).append('\n');
        }
        return dump.toString();
    }

    /**
     * Concatenates text/plain content and collects every named part as an attachment,
     * recursing into nested multiparts. A part counts as an attachment purely by having
     * a filename - inline text/html alternatives etc. have none and are skipped.
     */
    private static void collectParts(Object content, StringBuilder textOut, List<RawEmail.RawAttachment> attachmentsOut)
            throws Exception {
        if (content instanceof String s) {
            textOut.append(s);
            return;
        }
        if (!(content instanceof Multipart multipart)) {
            return;
        }
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String filename = part.getFileName();
            if (filename == null && part.isMimeType("text/plain")) {
                textOut.append(part.getContent());
            } else if (filename != null) {
                attachmentsOut.add(new RawEmail.RawAttachment(filename, Math.max(part.getSize(), 0), part.getContentType()));
            } else if (part.getContent() instanceof Multipart nested) {
                collectParts(nested, textOut, attachmentsOut);
            }
        }
    }
}
