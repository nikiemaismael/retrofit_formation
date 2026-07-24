package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

/**
 * Base de toutes les exceptions métier.
 * <p>
 * Ce type appartient au domaine : il ne dépend d'aucun framework web/HTTP.
 * Le message passé au constructeur est un message technique destiné
 * <b>uniquement aux logs</b> ; il n'est jamais renvoyé à l'utilisateur.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] messageArgs;

    protected BusinessException(ErrorCode errorCode, String devMessage, Object... messageArgs) {
        super(devMessage);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] messageArgs() {
        return messageArgs == null ? new Object[0] : messageArgs.clone();
    }
}
