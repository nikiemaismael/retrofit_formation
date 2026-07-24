package com.bank.core.infrastructure.adapter.in.web.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Corps de requête du virement (validation Jakarta Bean Validation). */
public record TransferRequest(

        @NotBlank(message = "{transfer.sourceIban.required}")
        String sourceIban,

        @NotBlank(message = "{transfer.targetIban.required}")
        @Pattern(regexp = "^[A-Z]{2}[0-9A-Z]{10,32}$", message = "{transfer.targetIban.invalid}")
        String targetIban,

        @NotNull(message = "{transfer.amount.required}")
        @Positive(message = "{transfer.amount.positive}")
        BigDecimal amount
) {
}
