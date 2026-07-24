package com.bank.core.infrastructure.adapter.in.web.error;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Fabrique les réponses {@link ApiError}.
 * <p>
 * Résout le message via {@link MessageSource} (i18n) et n'expose le message
 * technique ({@code debug}) que si {@code app.errors.expose-technical-details}
 * est activé (dev uniquement).
 */
@Component
public class ApiErrorFactory {

    private final MessageSource messageSource;
    private final ErrorProperties properties;

    public ApiErrorFactory(MessageSource messageSource, ErrorProperties properties) {
        this.messageSource = messageSource;
        this.properties = properties;
    }

    /**
     * Construit une réponse d'erreur avec un {@code errorId} déjà généré
     * (le même que celui poussé dans les logs).
     */
    public ApiError create(String errorId, ErrorCode code, String path, Locale locale,
                           Object[] msgArgs, List<ApiError.FieldViolation> violations,
                           Throwable source) {
        String message = messageSource.getMessage(
                code.messageKey(),
                msgArgs == null ? new Object[0] : msgArgs,
                code.messageKey(),
                locale == null ? Locale.getDefault() : locale);

        String debug = (properties.exposeTechnicalDetails() && source != null)
                ? source.getMessage()
                : null;

        return new ApiError(
                errorId,
                code.code(),
                code.httpStatus().value(),
                code.httpStatus().getReasonPhrase(),
                message,
                path,
                OffsetDateTime.now(),
                (violations == null || violations.isEmpty()) ? null : violations,
                debug);
    }

    public ApiError create(String errorId, ErrorCode code, String path, Locale locale) {
        return create(errorId, code, path, locale, new Object[0], null, null);
    }
}
