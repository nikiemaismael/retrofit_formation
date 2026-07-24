package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String iban) {
        super(ErrorCode.INSUFFICIENT_FUNDS, "Solde insuffisant sur %s".formatted(iban));
    }
}
