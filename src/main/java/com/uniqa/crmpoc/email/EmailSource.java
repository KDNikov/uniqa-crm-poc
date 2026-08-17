package com.uniqa.crmpoc.email;

import java.util.List;

/**
 * Abstraction over "where do incoming emails come from".
 *
 * POC implementation: GreenMailEmailSource (embedded in-memory IMAP server,
 * seeded with sample UNIQA-style emails - no external mailbox access needed).
 *
 * Production swap-in: implement this against Microsoft Graph API
 * (subscribe to /me/mailFolders/inbox/messages) once UNIQA grants Azure AD
 * app access. Everything else in the app - entities, NLP, rules, REST API -
 * stays unchanged.
 */
public interface EmailSource {
    List<RawEmail> fetchNewEmails();

    /**
     * Marks the given messageIds as durably processed so they aren't returned by a
     * future fetchNewEmails() call. Callers must only pass messageIds that were
     * actually persisted downstream - anything left unacknowledged should be
     * retried on the next poll. No-op by default.
     */
    default void acknowledge(List<String> messageIds) {
    }
}
