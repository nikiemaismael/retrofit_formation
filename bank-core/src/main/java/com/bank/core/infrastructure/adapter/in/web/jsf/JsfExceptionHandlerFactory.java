package com.bank.core.infrastructure.adapter.in.web.jsf;

import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;

/**
 * Enregistre {@link JsfExceptionHandler} dans le cycle de vie Faces.
 * À déclarer dans {@code META-INF/faces-config.xml}.
 */
public class JsfExceptionHandlerFactory extends ExceptionHandlerFactory {

    public JsfExceptionHandlerFactory(ExceptionHandlerFactory wrapped) {
        super(wrapped);
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        return new JsfExceptionHandler(getWrapped().getExceptionHandler());
    }
}
