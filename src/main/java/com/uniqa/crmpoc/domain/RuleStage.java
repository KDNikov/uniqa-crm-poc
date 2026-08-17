package com.uniqa.crmpoc.domain;

/**
 * Coarse-grained hierarchy tier a Rule belongs to. A rule in an earlier stage
 * always wins over a matching rule in a later stage, regardless of priority -
 * priority only breaks ties between rules within the same stage. This lets
 * business users express e.g. "compliance rules must always beat general
 * classification rules" without having to juggle a single flat priority number.
 */
public enum RuleStage {
    CRITICAL(2),
    STANDARD(1),
    FALLBACK(0);

    private final int weight;

    RuleStage(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
