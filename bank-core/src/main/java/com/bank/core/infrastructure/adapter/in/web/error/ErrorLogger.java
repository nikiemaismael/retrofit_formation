package com.bank.core.infrastructure.adapter.in.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Point de journalisation UNIQUE des erreurs.
 * <p>
 * Journalise l'erreur complète côté serveur (stack trace incluse pour les 5xx)
 * et corrèle chaque ligne via le MDC ({@code errorId}, {@code errorCode}).
 * Le contenu détaillé ne quitte jamais les logs.
 */
@Component
public class ErrorLogger {

    private static final Logger log = LoggerFactory.getLogger("com.bank.errors");

    public void log(String errorId, ErrorCode errorCode, String path, Throwable ex) {
        MDC.put("errorId", errorId);
        MDC.put("errorCode", errorCode.code());
        try {
            if (errorCode.logLevel() == Level.ERROR) {
                // 5xx : stack trace complète en ERROR
                log.error("Technical error - errorId={} code={} status={} path={}",
                        errorId, errorCode.code(), errorCode.httpStatus().value(), path, ex);
            } else {
                // 4xx métier : WARN sans stack (le détail va en DEBUG pour maîtriser le bruit)
                log.warn("Handled error - errorId={} code={} status={} path={} reason={}",
                        errorId, errorCode.code(), errorCode.httpStatus().value(), path,
                        ex == null ? "n/a" : ex.getMessage());
                if (log.isDebugEnabled() && ex != null) {
                    log.debug("Stack for errorId={}", errorId, ex);
                }
            }
        } finally {
            MDC.remove("errorId");
            MDC.remove("errorCode");
        }
    }
}
