package com.uniqa.crmpoc.controller;

import com.uniqa.crmpoc.domain.Rule;
import com.uniqa.crmpoc.domain.RuleCondition;
import com.uniqa.crmpoc.domain.RuleOperator;
import com.uniqa.crmpoc.dto.RuleConditionRequest;
import com.uniqa.crmpoc.dto.RuleRequest;
import com.uniqa.crmpoc.repository.RuleRepository;
import com.uniqa.crmpoc.rules.RuleEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Backs the rule-builder UI: business users create/edit/delete categorization
 * rules here. Every write rebuilds the Drools knowledge base so changes take
 * effect on the very next email processed - no redeploy needed.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRepository ruleRepository;
    private final RuleEngineService ruleEngineService;

    public RuleController(RuleRepository ruleRepository, RuleEngineService ruleEngineService) {
        this.ruleRepository = ruleRepository;
        this.ruleEngineService = ruleEngineService;
    }

    @GetMapping
    public List<Rule> listAll() {
        return ruleRepository.findAll();
    }

    @PostMapping
    @Transactional
    public Rule create(@Valid @RequestBody RuleRequest req) {
        validate(req);
        Rule rule = toEntity(new Rule(), req);
        Rule saved = ruleRepository.save(rule);
        ruleEngineService.rebuild();
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public Rule update(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
        validate(req);
        Rule rule = ruleRepository.findById(id).orElseThrow();
        Rule saved = ruleRepository.save(toEntity(rule, req));
        ruleEngineService.rebuild();
        return saved;
    }

    private void validate(RuleRequest req) {
        for (RuleConditionRequest c : req.conditions()) {
            if (c.operator().requiresValue()) {
                boolean hasUsableValue = c.values() != null
                        && c.values().stream().anyMatch(v -> v != null && !v.isBlank());
                if (!hasUsableValue) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "At least one non-blank value is required for operator " + c.operator());
                }
            }
            if (c.operator() == RuleOperator.MATCHES_REGEX && c.values() != null) {
                for (String v : c.values()) {
                    if (v == null || v.isBlank()) continue;
                    try {
                        Pattern.compile(v);
                    } catch (PatternSyntaxException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Invalid regex '" + v + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ruleRepository.deleteById(id);
        ruleEngineService.rebuild();
    }

    private Rule toEntity(Rule rule, RuleRequest req) {
        rule.setName(req.name());
        rule.setDescription(req.description());
        List<RuleCondition> conditions = req.conditions().stream().map(c -> {
            RuleCondition condition = new RuleCondition();
            condition.setField(c.field());
            condition.setOperator(c.operator());
            List<String> values = c.operator().requiresValue() && c.values() != null
                    ? c.values().stream().filter(v -> v != null && !v.isBlank()).toList()
                    : List.of();
            condition.setValues(new ArrayList<>(values));
            return condition;
        }).toList();
        rule.replaceConditions(conditions);
        rule.setRequireNegativeSentiment(req.requireNegativeSentiment());
        rule.setTargetCategoryName(req.targetCategoryName());
        rule.setStage(req.stage());
        rule.setPriority(req.priority());
        rule.setActive(req.active());
        return rule;
    }
}
