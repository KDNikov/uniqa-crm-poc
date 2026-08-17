package com.uniqa.crmpoc.dto;

import com.uniqa.crmpoc.domain.RuleField;
import com.uniqa.crmpoc.domain.RuleOperator;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** One (field, operator, values) test submitted as part of a RuleRequest. */
public record RuleConditionRequest(
        @NotNull RuleField field,
        @NotNull RuleOperator operator,
        List<String> values
) {}
