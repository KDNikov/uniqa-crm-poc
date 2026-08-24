package com.uniqa.crmpoc.nlp;

/** spamScore is a 0.0-1.0 heuristic likelihood, not a verdict - a human confirms or dismisses it. */
public record NlpResult(String category, double confidence, boolean negativeSentiment, double spamScore) {}
