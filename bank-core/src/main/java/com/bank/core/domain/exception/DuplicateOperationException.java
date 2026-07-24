package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

public class DuplicateOperationException extends BusinessException {

    public DuplicateOperationException(String operationRef) {
        super(ErrorCode.DUPLICATE_OPERATION, "Opération déjà traitée: %s".formatted(operationRef));
    }
}
