package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
                "%s introuvable: %s".formatted(resource, id),
                resource, id);
    }
}
