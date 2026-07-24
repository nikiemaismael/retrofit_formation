package com.bank.core.infrastructure.adapter.in.web.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés applicatives de gestion d'erreurs (préfixe {@code app.errors}).
 *
 * @param exposeTechnicalDetails true UNIQUEMENT en dev : ajoute un champ {@code debug}
 *                               à la réponse. Doit rester false en production.
 * @param errorPage              page d'erreur JSF générique.
 */
@ConfigurationProperties(prefix = "app.errors")
public record ErrorProperties(
        boolean exposeTechnicalDetails,
        String errorPage
) {
    public ErrorProperties {
        if (errorPage == null || errorPage.isBlank()) {
            errorPage = "/error.xhtml";
        }
    }
}
