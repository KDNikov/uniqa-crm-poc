package com.uniqa.crmpoc.service;

import com.uniqa.crmpoc.domain.Email;
import com.uniqa.crmpoc.domain.EmailAttachment;
import com.uniqa.crmpoc.email.RawEmail;
import com.uniqa.crmpoc.nlp.NlpCategorizationService;
import static com.uniqa.crmpoc.nlp.NlpCategorizationService.AUTO_SPAM_THRESHOLD;
import com.uniqa.crmpoc.nlp.NlpResult;
import com.uniqa.crmpoc.repository.EmailRepository;
import com.uniqa.crmpoc.rules.EmailFact;
import com.uniqa.crmpoc.rules.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single email through the pipeline:
 *   raw email -> NLP category guess + sentiment -> Drools rule evaluation
 *   -> final category -> persisted Email row.
 *
 * Rule verdicts win over the raw NLP guess when a rule matches; otherwise
 * the NLP guess is used directly so every email still gets categorized.
 */
@Service
@Slf4j
public class EmailProcessingService {

    private final NlpCategorizationService nlpService;
    private final RuleEngineService ruleEngineService;
    private final EmailRepository emailRepository;

    public EmailProcessingService(NlpCategorizationService nlpService,
                                   RuleEngineService ruleEngineService,
                                   EmailRepository emailRepository) {
        this.nlpService = nlpService;
        this.ruleEngineService = ruleEngineService;
        this.emailRepository = emailRepository;
    }

    public Email process(RawEmail raw) {
        if (emailRepository.findByMessageId(raw.messageId()).isPresent()) {
            log.debug("Skipping already-ingested email {}", raw.messageId());
            return null;
        }

        NlpResult nlp = nlpService.categorize(raw.subject(), raw.body());

        EmailFact fact = new EmailFact(
                raw.subject(), raw.body(), nlp.category(), nlp.confidence(), nlp.negativeSentiment());
        ruleEngineService.evaluate(fact);

        String finalCategory = fact.getSuggestedCategory() != null
                ? fact.getSuggestedCategory()
                : nlp.category(); // no rule matched - fall back to the NLP guess

        Email email = new Email();
        email.setMessageId(raw.messageId());
        email.setReceivingAccount(raw.mailboxAddress());
        email.setFromAddress(raw.fromAddress());
        email.setSubject(raw.subject());
        email.setBody(raw.body());
        email.setReceivedAt(raw.receivedAt());
        email.setHasAttachment(raw.hasAttachment());
        email.setToAddresses(String.join(", ", raw.toAddresses()));
        email.setCcAddresses(String.join(", ", raw.ccAddresses()));
        email.setReplyTo(raw.replyTo());
        email.setInReplyTo(raw.inReplyTo());
        email.setReferencesHeader(raw.referencesHeader());
        email.setRawHeaders(raw.rawHeaders());
        email.setAttachments(raw.attachments().stream().map(a -> {
            EmailAttachment attachment = new EmailAttachment();
            attachment.setFilename(a.filename());
            attachment.setSizeBytes(a.sizeBytes());
            attachment.setContentType(a.contentType());
            return attachment;
        }).toList());
        email.setNlpCategory(nlp.category());
        email.setNlpConfidence(nlp.confidence());
        email.setNegativeSentiment(nlp.negativeSentiment());
        email.setSpamScore(nlp.spamScore());
        // High-confidence spam is auto-marked, no human click needed; mid-confidence stays a suggestion.
        if (nlp.spamScore() >= AUTO_SPAM_THRESHOLD) {
            email.setSpam(true);
            email.setSpamSuggestionDismissed(true);
        }
        email.setSuggestedCategory(fact.getSuggestedCategory());
        email.setMatchedRuleId(fact.getMatchedRuleId());
        email.setFinalCategory(finalCategory);
        email.setProcessed(true);

        Email saved = emailRepository.save(email);
        log.info("Processed email '{}' -> category={} (rule={}, nlpGuess={}, nlpConfidence={})",
                raw.subject(), finalCategory, fact.getMatchedRuleId(), nlp.category(), nlp.confidence());
        return saved;
    }
}
