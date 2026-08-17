package com.uniqa.crmpoc.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single user-defined categorization rule, e.g.:
 *   "IF body CONTAINS 'claim' AND sentiment is negative -> Complaints"
 *
 * A rule can hold several conditions (AND'd together, see RuleCondition), each
 * of which can itself test several values (OR'd together) - e.g. "body STARTS_WITH
 * 'Dear' or 'Hello'" AND "subject CONTAINS 'urgent'".
 *
 * These rows are what the rule-builder UI creates/edits. RuleEngineService
 * compiles the active rules into Drools DRL at runtime, so business users
 * never touch DRL directly.
 */
@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    // EAGER: rule count is tiny (POC scale) and both RuleEngineService.rebuild() and the
    // GET /api/rules listing touch conditions outside a transaction (open-in-view is off).
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "condition_order")
    private List<RuleCondition> conditions = new ArrayList<>();

    /** Only match if the NLP sentiment heuristic flagged the email as negative. */
    @Column(nullable = false)
    private boolean requireNegativeSentiment = false;

    /** Name of the target Category this rule assigns when matched. */
    @Column(nullable = false)
    private String targetCategoryName;

    /** Coarse hierarchy tier; an earlier stage always beats a later one regardless of priority. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleStage stage = RuleStage.STANDARD;

    /** Within the same stage, higher runs first. Ties broken by insertion order. */
    @Column(nullable = false)
    private int priority = 0;

    @Column(nullable = false)
    private boolean active = true;

    /** Replaces all conditions, wiring the back-reference so cascade/orphanRemoval work. */
    public void replaceConditions(List<RuleCondition> newConditions) {
        conditions.clear();
        for (RuleCondition condition : newConditions) {
            condition.setRule(this);
            conditions.add(condition);
        }
    }
}
