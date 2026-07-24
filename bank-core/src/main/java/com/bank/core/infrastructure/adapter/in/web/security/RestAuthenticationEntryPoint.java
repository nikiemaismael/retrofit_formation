package com.bank.core.infrastructure.adapter.in.web.security;

import com.bank.core.infrastructure.adapter.in.web.error.ApiError;
import com.bank.core.infrastructure.adapter.in.web.error.ApiErrorFactory;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorIdGenerator;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 — utilisateur NON authentifié tentant d'accéder à une ressource protégée.
 * Écrit directement une réponse JSON {@link ApiError} car l'exception survient
 * dans la chaîne de filtres Spring Security (avant Spring MVC).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorFactory factory;
    private final ErrorLogger errorLogger;
    private final ErrorIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ApiErrorFactory factory, ErrorLogger errorLogger,
                                        ErrorIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.factory = factory;
        this.errorLogger = errorLogger;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityErrorResponder.write(request, response, ErrorCode.UNAUTHENTICATED, authException,
                factory, errorLogger, idGenerator, objectMapper);
    }
}
