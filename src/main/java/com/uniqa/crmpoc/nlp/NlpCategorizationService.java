package com.uniqa.crmpoc.nlp;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Local, in-process NLP - no external API calls. Trains a small Apache
 * OpenNLP document categorizer at startup from src/main/resources/nlp/training-data.txt
 * to produce a category guess + confidence for each incoming email.
 *
 * This feeds the Drools rule engine as a fact; it does not decide the final
 * category by itself unless no user-defined rule matches (see RuleEngineService).
 *
 * Sentiment is a lightweight keyword heuristic layered on top - a POC
 * stand-in for a real sentiment model, kept simple and explainable on purpose.
 */
@Service
@Slf4j
public class NlpCategorizationService {

    private DocumentCategorizerME categorizer;

    private static final Set<String> NEGATIVE_KEYWORDS = Set.of(
            "disappointed", "frustrated", "unacceptable", "angry", "furious",
            "worst", "terrible", "cancel", "cancelling", "complaint", "unhappy",
            "ignored", "wasted", "disconnected"
    );

    @PostConstruct
    public void trainModel() throws Exception {
        try (InputStream in = new ClassPathResource("nlp/training-data.txt").getInputStream()) {
            ObjectStream<String> lineStream = new PlainTextByLineStream(
                    () -> in, StandardCharsets.UTF_8);
            ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

            TrainingParameters params = TrainingParameters.defaultParams();
            DoccatModel model = DocumentCategorizerME.train("en", sampleStream, params, new DoccatFactory());
            categorizer = new DocumentCategorizerME(model);
            log.info("OpenNLP document categorizer trained with {} categories",
                    categorizer.getNumberOfCategories());
        }
    }

    public NlpResult categorize(String subject, String body) {
        String text = (subject == null ? "" : subject) + " " + (body == null ? "" : body);
        String[] tokens = opennlp.tools.tokenize.SimpleTokenizer.INSTANCE.tokenize(text);
        double[] outcomes = categorizer.categorize(tokens);
        String bestCategory = categorizer.getBestCategory(outcomes);
        double confidence = outcomes[categorizer.getIndex(bestCategory)];

        boolean negative = containsNegativeSentiment(text);

        return new NlpResult(bestCategory, confidence, negative);
    }

    private boolean containsNegativeSentiment(String text) {
        String lower = text.toLowerCase();
        return NEGATIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
