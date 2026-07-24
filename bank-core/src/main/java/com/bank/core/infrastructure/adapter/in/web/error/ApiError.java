package com.bank.core.infrastructure.adapter.in.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse d'erreur EXPOSÉE au client. Contient uniquement des champs sûrs.
 * <p>
 * Ne contient JAMAIS : stack trace, message SQL, nom de classe, cause,
 * chemin de fichier. Le champ {@code debug} n'est renseigné qu'en profil
 * non-production (piloté par {@code app.errors.expose-technical-details}) et
 * omis du JSON lorsqu'il est {@code null} ({@link JsonInclude.Include#NON_NULL}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String errorId,
        String code,
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<FieldViolation> violations,
        String debug
) {
    public record FieldViolation(String field, String message) {
    }
}
