package com.uniqa.crmpoc.rules;

import lombok.Getter;
import lombok.Setter;

/**
 * The working memory fact Drools reasons over. Built from an Email plus its
 * NLP enrichment before the KieSession fires; suggestedCategory/matchedRuleId
 * are written back by whichever rule fires (or left null if none match, in
 * which case the NLP guess is used as a fallback - see RuleEngineService).
 */
@Getter
@Setter
public class EmailFact {

    private String subject;
    private String body;
    private String subjectLower;
    private String bodyLower;

    private String nlpCategory;
    private double nlpConfidence;
    private boolean negativeSentiment;

    private String suggestedCategory;
    private Long matchedRuleId;

    public EmailFact(String subject, String body, String nlpCategory,
                      double nlpConfidence, boolean negativeSentiment) {
        this.subject = subject == null ? "" : subject;
        this.body = body == null ? "" : body;
        this.subjectLower = this.subject.toLowerCase();
        this.bodyLower = this.body.toLowerCase();
        this.nlpCategory = nlpCategory;
        this.nlpConfidence = nlpConfidence;
        this.negativeSentiment = negativeSentiment;
    }
}
