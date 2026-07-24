package com.bank.core.infrastructure.adapter.in.web.error;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * Catalogue central des erreurs — CONTRAT PUBLIC versionné.
 * <p>
 * Le {@code code} (BANK-xxxx) est stable : les tiers s'appuient dessus. Le
 * message réellement affiché est résolu via {@code MessageSource} (clé i18n),
 * jamais en dur ici.
 */
public enum ErrorCode {

    // 4xx — erreurs client / métier
    VALIDATION_ERROR("BANK-1000", HttpStatus.BAD_REQUEST, "error.validation", Level.WARN),
    RESOURCE_NOT_FOUND("BANK-1001", HttpStatus.NOT_FOUND, "error.resource.notfound", Level.WARN),
    INSUFFICIENT_FUNDS("BANK-1002", HttpStatus.UNPROCESSABLE_ENTITY, "error.funds.insufficient", Level.WARN),
    ACCOUNT_BLOCKED("BANK-1003", HttpStatus.FORBIDDEN, "error.account.blocked", Level.WARN),
    DUPLICATE_OPERATION("BANK-1004", HttpStatus.CONFLICT, "error.operation.duplicate", Level.WARN),
    CONCURRENT_UPDATE("BANK-1005", HttpStatus.CONFLICT, "error.concurrent.update", Level.WARN),
    MALFORMED_REQUEST("BANK-1006", HttpStatus.BAD_REQUEST, "error.request.malformed", Level.WARN),
    METHOD_NOT_ALLOWED("BANK-1007", HttpStatus.METHOD_NOT_ALLOWED, "error.method.notallowed", Level.WARN),
    UNSUPPORTED_MEDIA_TYPE("BANK-1008", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.media.unsupported", Level.WARN),

    // 401 / 403 — sécurité
    UNAUTHENTICATED("BANK-2000", HttpStatus.UNAUTHORIZED, "error.auth.required", Level.WARN),
    ACCESS_DENIED("BANK-2001", HttpStatus.FORBIDDEN, "error.access.denied", Level.WARN),

    // 5xx — erreurs techniques (aucun détail exposé)
    INTERNAL_ERROR("BANK-9000", HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", Level.ERROR),
    SERVICE_UNAVAILABLE("BANK-9001", HttpStatus.SERVICE_UNAVAILABLE, "error.service.unavailable", Level.ERROR),
    DATA_ACCESS_ERROR("BANK-9002", HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", Level.ERROR);

    private final String code;
    private final HttpStatus httpStatus;
    private final String messageKey;
    private final Level logLevel;

    ErrorCode(String code, HttpStatus httpStatus, String messageKey, Level logLevel) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
        this.logLevel = logLevel;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }

    public Level logLevel() {
        return logLevel;
    }
}
