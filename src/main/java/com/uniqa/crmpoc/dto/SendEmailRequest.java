package com.uniqa.crmpoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** What the CRM inbox view submits for Reply/Reply All/Forward. */
public record SendEmailRequest(
        @NotBlank String fromAddress,
        @NotEmpty List<String> to,
        List<String> cc,
        @NotBlank String subject,
        @NotBlank String body
) {}
