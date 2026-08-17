package com.uniqa.crmpoc.dto;

import jakarta.validation.constraints.NotBlank;

/** What the rule-builder UI submits when a business user creates a category. */
public record CategoryRequest(
        @NotBlank String name,
        String description
) {}
