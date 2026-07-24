package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

public class ConcurrentModificationBusinessException extends BusinessException {

    public ConcurrentModificationBusinessException(String resource) {
        super(ErrorCode.CONCURRENT_UPDATE, "Modification concurrente détectée: %s".formatted(resource));
    }
}
