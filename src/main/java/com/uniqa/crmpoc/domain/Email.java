package com.uniqa.crmpoc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single ingested email. Populated by EmailIntakeScheduler, enriched by
 * NlpCategorizationService and RuleEngineService.
 */
@Entity
@Table(name = "emails")
@Getter
@Setter
@NoArgsConstructor
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Message-ID header, used to avoid re-ingesting the same email. */
    @Column(nullable = false, unique = true)
    private String messageId;

    private String fromAddress;

    @Column(length = 1000)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    private Instant receivedAt;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean hasAttachment = false;

    // --- Metadata ---
    /** Which of the configured MailAccount inboxes this was fetched from. */
    private String receivingAccount;

    @Column(columnDefinition = "text")
    private String toAddresses;

    @Column(columnDefinition = "text")
    private String ccAddresses;

    private String replyTo;

    /** Message-ID of the email this one is replying to, if any (threading). */
    private String inReplyTo;

    /** Raw References header (RFC 5322 thread chain of Message-IDs), if any. */
    @Column(name = "references_header", columnDefinition = "text")
    private String referencesHeader;

    /** Every header verbatim, one "Name: value" per line - for audit/future use. */
    @Column(columnDefinition = "text")
    private String rawHeaders;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "email_id")
    @OrderColumn(name = "attachment_order")
    private List<EmailAttachment> attachments = new ArrayList<>();

    // --- NLP enrichment ---
    private String nlpCategory;
    private Double nlpConfidence;
    private Boolean negativeSentiment;

    // --- Rule engine outcome ---
    private String suggestedCategory;
    private Long matchedRuleId;

    /** Final category shown in the CRM inbox view; defaults to suggestedCategory but can be overridden by a human. */
    private String finalCategory;

    @Column(nullable = false)
    private boolean processed = false;

    /** Soft-delete: archived emails are excluded from the active inbox views. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean archived = false;

    /** User-flagged as most urgent to handle. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean important = false;

    /** Confirmed spam - auto-set at ingestion above NlpCategorizationService.AUTO_SPAM_THRESHOLD,
     *  or set by a human directly / by accepting the NLP suggestion below.
     *  Spam emails are NOT archived/hidden: they stay visible in the inbox, just badged. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean spam = false;

    /** NLP-heuristic spam likelihood (0.0-1.0) computed at ingestion; a suggestion, not a verdict. */
    private Double spamScore;

    /** A human dismissed the "likely spam" suggestion, so it stops being surfaced. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean spamSuggestionDismissed = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
