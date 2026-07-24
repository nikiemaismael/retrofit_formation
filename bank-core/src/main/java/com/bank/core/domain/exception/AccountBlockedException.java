package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

public class AccountBlockedException extends BusinessException {

    public AccountBlockedException(String iban) {
        super(ErrorCode.ACCOUNT_BLOCKED, "Compte bloqué: %s".formatted(iban));
    }
}
