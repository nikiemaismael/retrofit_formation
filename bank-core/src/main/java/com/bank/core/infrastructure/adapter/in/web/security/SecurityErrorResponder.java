package com.bank.core.infrastructure.adapter.in.web.security;

import com.bank.core.infrastructure.adapter.in.web.error.ApiError;
import com.bank.core.infrastructure.adapter.in.web.error.ApiErrorFactory;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorIdGenerator;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Fabrique et sérialise une réponse d'erreur de sécurité (401/403) en JSON,
 * en réutilisant la même mécanique errorId + log + i18n que le handler REST.
 */
final class SecurityErrorResponder {

    private SecurityErrorResponder() {
    }

    static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
                      Exception ex, ApiErrorFactory factory, ErrorLogger errorLogger,
                      ErrorIdGenerator idGenerator, ObjectMapper mapper) throws IOException {
        String errorId = idGenerator.newErrorId();
        String path = request.getRequestURI();
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, request.getLocale(),
                new Object[0], null, ex);
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
