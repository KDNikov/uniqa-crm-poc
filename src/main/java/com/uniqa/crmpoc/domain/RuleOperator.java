package com.uniqa.crmpoc.domain;

/** Comparison applied by a Rule condition. */
public enum RuleOperator {
    CONTAINS,
    NOT_CONTAINS,
    EQUALS,
    NOT_EQUALS,
    STARTS_WITH,
    NOT_STARTS_WITH,
    ENDS_WITH,
    NOT_ENDS_WITH,
    IS_EMPTY,
    IS_NOT_EMPTY,
    MATCHES_REGEX;

    /** IS_EMPTY/IS_NOT_EMPTY test presence, not content, so they take no value. */
    public boolean requiresValue() {
        return this != IS_EMPTY && this != IS_NOT_EMPTY;
    }
}
