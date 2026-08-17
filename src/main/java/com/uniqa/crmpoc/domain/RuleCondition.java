package com.uniqa.crmpoc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One (field, operator, values) test within a Rule. Multiple conditions on the
 * same Rule are AND'd together; multiple values within one condition are OR'd -
 * e.g. field=BODY, operator=STARTS_WITH, values=["Dear","Hello"] matches a body
 * that starts with either "Dear" or "Hello".
 */
@Entity
@Table(name = "rule_conditions")
@Getter
@Setter
@NoArgsConstructor
public class RuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleField field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleOperator operator;

    /** Empty for value-less operators like IS_EMPTY/IS_NOT_EMPTY. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rule_condition_values", joinColumns = @JoinColumn(name = "rule_condition_id"))
    @OrderColumn(name = "value_order")
    // "value" is a reserved word in H2 (though fine in Postgres) - named to work on both.
    @Column(name = "condition_value")
    private List<String> values = new ArrayList<>();
}
