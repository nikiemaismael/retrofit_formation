package com.bank.core.infrastructure.adapter.in.web.security;

import com.bank.core.infrastructure.adapter.in.web.error.ApiErrorFactory;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorIdGenerator;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 — utilisateur authentifié mais SANS le droit requis.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorFactory factory;
    private final ErrorLogger errorLogger;
    private final ErrorIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ApiErrorFactory factory, ErrorLogger errorLogger,
                                   ErrorIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.factory = factory;
        this.errorLogger = errorLogger;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        SecurityErrorResponder.write(request, response, ErrorCode.ACCESS_DENIED, ex,
                factory, errorLogger, idGenerator, objectMapper);
    }
}
