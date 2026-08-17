package com.uniqa.crmpoc.dto;

import jakarta.validation.constraints.NotBlank;

/** What the CRM inbox view submits when a human overrides an email's auto-assigned category. */
public record CategoryOverrideRequest(@NotBlank String category) {}
