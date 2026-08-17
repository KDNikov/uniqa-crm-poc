package com.uniqa.crmpoc.rules;

import com.uniqa.crmpoc.domain.Rule;
import com.uniqa.crmpoc.domain.RuleCondition;
import com.uniqa.crmpoc.domain.RuleField;
import com.uniqa.crmpoc.domain.RuleOperator;
import com.uniqa.crmpoc.repository.RuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles the active, DB-backed Rule rows (the ones the rule-builder UI
 * manages) into Drools DRL at runtime, and evaluates emails against them.
 *
 * This is the bridge between "business user drags a condition into a UI"
 * and an actual rule engine: no DRL is ever hand-written by the user, but
 * Drools still does the evaluation. Call rebuild() after any Rule CRUD
 * operation so new/edited rules take effect immediately.
 */
@Service
@Slf4j
public class RuleEngineService {

    /**
     * Weight multiplier for RuleStage so stage always dominates priority in the
     * computed salience, e.g. the lowest-priority CRITICAL rule (salience 2_000_000)
     * still outranks the highest-priority STANDARD rule (salience < 2_000_000).
     */
    private static final int STAGE_WEIGHT = 1_000_000;

    private final RuleRepository ruleRepository;
    private volatile KieContainer kieContainer;

    public RuleEngineService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    public void init() {
        rebuild();
    }

    /** Recompiles the Drools knowledge base from whatever rules are currently active in the DB. */
    public synchronized void rebuild() {
        List<Rule> activeRules = ruleRepository.findByActiveTrue().stream()
                .sorted(Comparator
                        .comparingInt((Rule r) -> r.getStage().getWeight()).reversed()
                        .thenComparing(Comparator.comparingInt(Rule::getPriority).reversed())
                        .thenComparing(Rule::getId))
                .toList();
        String drl = generateDrl(activeRules);

        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.write("src/main/resources/rules/generated.drl", drl);

        KieBuilder builder = kieServices.newKieBuilder(kfs);
        builder.buildAll();

        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            log.error("Drools compile errors: {}", builder.getResults().getMessages());
            throw new IllegalStateException("Failed to compile generated DRL: " + builder.getResults());
        }

        this.kieContainer = kieServices.newKieContainer(builder.getKieModule().getReleaseId());
        log.info("Rule engine rebuilt with {} active rule(s)", activeRules.size());
    }

    /**
     * Fires the compiled rules against a fact. If no rule matches, suggestedCategory
     * stays null - callers should fall back to the raw NLP guess in that case.
     */
    public EmailFact evaluate(EmailFact fact) {
        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(fact);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return fact;
    }

    private String generateDrl(List<Rule> rules) {
        StringBuilder drl = new StringBuilder();
        drl.append("package com.uniqa.crmpoc.rules.generated;\n")
           .append("import com.uniqa.crmpoc.rules.EmailFact;\n\n");

        for (Rule rule : rules) {
            int salience = rule.getStage().getWeight() * STAGE_WEIGHT + rule.getPriority();
            drl.append("rule \"rule_").append(rule.getId()).append("_").append(sanitize(rule.getName())).append("\"\n")
               .append("    salience ").append(salience).append("\n")
               .append("    no-loop true\n")
               .append("    when\n")
               // suggestedCategory == null guard, combined with the modify() block below
               // (which re-triggers matching), is what makes the highest-salience match
               // win: once a rule fires, every lower-salience rule's guard goes false.
               .append("        $email : EmailFact( suggestedCategory == null, ").append(buildCondition(rule)).append(" )\n")
               .append("    then\n")
               .append("        modify($email) {\n")
               .append("            setSuggestedCategory(\"").append(escape(rule.getTargetCategoryName())).append("\"),\n")
               .append("            setMatchedRuleId(").append(rule.getId()).append("L)\n")
               .append("        }\n")
               .append("end\n\n");
        }
        return drl.toString();
    }

    /** Every condition in the rule must hold (AND); rule.conditions is never empty (validated on write). */
    private String buildCondition(Rule rule) {
        String allConditions = rule.getConditions().stream()
                .map(this::conditionExpr)
                .collect(Collectors.joining(" && "));
        if (rule.isRequireNegativeSentiment()) {
            return allConditions + ", negativeSentiment == true";
        }
        return allConditions;
    }

    /** A single condition's values are OR'd together - e.g. body STARTS_WITH "Dear" or "Hello". */
    private String conditionExpr(RuleCondition condition) {
        boolean caseSensitive = condition.getOperator() == RuleOperator.MATCHES_REGEX;
        List<String> rawValues = condition.getOperator().requiresValue() ? condition.getValues() : List.of("");

        String valuesExpr = rawValues.stream()
                .map(v -> escape(caseSensitive ? v : v.toLowerCase()))
                .map(v -> fieldOperatorExpr(condition.getField(), condition.getOperator(), v, caseSensitive))
                .collect(Collectors.joining(" || "));
        return "(" + valuesExpr + ")";
    }

    private String fieldOperatorExpr(RuleField field, RuleOperator op, String value, boolean caseSensitive) {
        return switch (field) {
            case SUBJECT -> operatorExpr(fieldExpr("subject", caseSensitive), op, value);
            case BODY -> operatorExpr(fieldExpr("body", caseSensitive), op, value);
            case SUBJECT_OR_BODY -> "(" + operatorExpr(fieldExpr("subject", caseSensitive), op, value)
                    + " || " + operatorExpr(fieldExpr("body", caseSensitive), op, value) + ")";
        };
    }

    private String fieldExpr(String base, boolean caseSensitive) {
        return caseSensitive ? base : base + "Lower";
    }

    private String operatorExpr(String field, RuleOperator op, String value) {
        return switch (op) {
            case CONTAINS -> field + ".contains(\"" + value + "\")";
            case NOT_CONTAINS -> "!" + field + ".contains(\"" + value + "\")";
            case EQUALS -> field + " == \"" + value + "\"";
            case NOT_EQUALS -> field + " != \"" + value + "\"";
            case STARTS_WITH -> field + ".startsWith(\"" + value + "\")";
            case NOT_STARTS_WITH -> "!" + field + ".startsWith(\"" + value + "\")";
            case ENDS_WITH -> field + ".endsWith(\"" + value + "\")";
            case NOT_ENDS_WITH -> "!" + field + ".endsWith(\"" + value + "\")";
            case IS_EMPTY -> field + ".isEmpty()";
            case IS_NOT_EMPTY -> "!" + field + ".isEmpty()";
            // String.matches() requires a full-string match; authors wrap with .*...*
            // themselves for a partial match. Regex validity is checked in RuleController.
            case MATCHES_REGEX -> field + ".matches(\"" + value + "\")";
        };
    }

    private String sanitize(String s) {
        return s == null ? "rule" : s.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
