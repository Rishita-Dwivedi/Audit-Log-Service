package com.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RedactRequest(
        @NotEmpty List<@NotBlank String> fields,
        @NotBlank String reason
) {
}
