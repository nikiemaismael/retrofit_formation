# Architecture de gestion centralisée des exceptions — Application bancaire

**Stack cible** : Spring Boot 3.x · Java 17 · Architecture hexagonale · Spring Security 6 · PrimeFaces (JSF / Jakarta Faces) · Hibernate/JPA

**Objectif principal** : garantir **zéro fuite** de détails techniques (stack trace, message SQL, nom de classe, chemin interne, exception native) vers l'utilisateur final ou les tiers, **tout en conservant des logs complets et corrélables via un `ErrorId`** unique remis à l'utilisateur.

---

## 1. Principes directeurs

| Principe | Traduction concrète |
|---|---|
| **Fail safe / secure by default** | Toute exception non explicitement mappée → réponse générique 500 + `ErrorId`, jamais le détail technique. |
| **Séparation domaine / infrastructure** | Le domaine lève des exceptions métier pures (sans dépendance Spring/HTTP). L'infrastructure (adapters web) les traduit en réponses. |
| **Un point d'entrée unique par canal** | REST : `@RestControllerAdvice`. JSF : `ExceptionHandlerFactory`. Sécurité : `AuthenticationEntryPoint` + `AccessDeniedHandler`. |
| **Corrélation systématique** | Chaque erreur = un `ErrorId` propagé dans le log (MDC) **et** dans la réponse. L'utilisateur donne l'`ErrorId` au support → on retrouve la trace complète. |
| **Catalogue d'erreurs stable** | Un `enum ErrorCode` = contrat public versionné (codes `BANK-XXXX`) indépendant des messages et du HTTP status. |
| **Messages i18n** | Les messages destinés à l'utilisateur passent par `MessageSource` (jamais le message d'exception brut). |

---

## 2. Architecture des packages

L'architecture hexagonale isole le **domaine** (règles métier, exceptions métier pures) des **adapters** (REST, JSF, sécurité, persistance). La traduction exception → réponse vit dans l'adapter *web*, jamais dans le domaine.

```
com.bank.core
│
├── domain
│   └── exception                         # Exceptions MÉTIER pures (aucun import Spring/Jakarta/HTTP)
│       ├── BusinessException.java         # Classe de base abstraite (porte un ErrorCode)
│       ├── ResourceNotFoundException.java
│       ├── InsufficientFundsException.java
│       ├── AccountBlockedException.java
│       ├── DuplicateOperationException.java
│       └── ConcurrentModificationBusinessException.java
│
├── application                            # Cas d'usage (ports). Peut relancer des BusinessException.
│   └── exception
│       └── UseCaseValidationException.java
│
└── infrastructure
    └── adapter
        └── in
            └── web
                ├── error                              # NOYAU centralisé REST
                │   ├── ErrorCode.java                 # enum : code public + HTTP status + clé i18n
                │   ├── ApiError.java                  # Modèle de réponse JSON (DTO immuable)
                │   ├── ApiErrorFactory.java           # Construit ApiError (+ ErrorId, MDC, i18n)
                │   ├── ErrorIdGenerator.java          # Génération de l'ErrorId
                │   ├── ErrorLogger.java               # Journalisation (niveau selon gravité)
                │   └── GlobalRestExceptionHandler.java# @RestControllerAdvice (point d'entrée REST)
                │
                ├── security
                │   ├── RestAuthenticationEntryPoint.java  # 401 non authentifié
                │   └── RestAccessDeniedHandler.java       # 403 accès refusé
                │
                └── jsf
                    ├── JsfExceptionHandler.java            # ExceptionHandlerWrapper
                    └── JsfExceptionHandlerFactory.java     # Factory déclarée dans faces-config.xml
```

> **Règle d'or** : le package `domain.exception` **ne doit importer ni Spring, ni Jakarta Servlet/Faces, ni HTTP**. Il ne connaît que `ErrorCode` (qui peut vivre dans le domaine si on veut un contrat purement métier, ou dans l'infra web si le code est un contrat d'API). Ici on place `ErrorCode` côté web car c'est un **contrat d'API exposé aux tiers**, et le domaine référence l'enum via son package. Alternative défendable : `ErrorCode` dans `domain` et le mapping HTTP dans l'infra.

---

## 3. Responsabilités classe par classe

| Classe | Responsabilité | Ne fait PAS |
|---|---|---|
| `BusinessException` | Base de toutes les erreurs métier ; porte un `ErrorCode` + arguments d'i18n. | Ne connaît pas HTTP ni JSON. |
| `ErrorCode` (enum) | Contrat stable : `codePublic` (`BANK-1001`), `httpStatus`, `messageKey`, `logLevel`. | Ne contient pas de message localisé en dur. |
| `ApiError` | DTO **immuable** sérialisé en JSON. Champs sûrs uniquement. | Ne contient jamais stack trace / SQL / classe. |
| `ApiErrorFactory` | Fabrique un `ApiError` : génère l'`ErrorId`, résout le message i18n, remplit les métadonnées. | Ne décide pas du logging. |
| `ErrorIdGenerator` | Produit un identifiant unique, court, non devinable, horodaté. | — |
| `ErrorLogger` | Journalise avec le bon niveau (WARN métier / ERROR technique) + MDC (`errorId`, `traceId`). | N'écrit rien dans la réponse. |
| `GlobalRestExceptionHandler` | **Point d'entrée REST unique** : intercepte toutes les exceptions, log via `ErrorLogger`, répond via `ApiErrorFactory`. | Ne contient pas de logique métier. |
| `RestAuthenticationEntryPoint` | Répond 401 (JSON `ApiError`) quand l'utilisateur n'est pas authentifié. | — |
| `RestAccessDeniedHandler` | Répond 403 (JSON `ApiError`) quand l'utilisateur authentifié n'a pas le droit. | — |
| `JsfExceptionHandler` | Intercepte les exceptions non gérées du cycle JSF, log, redirige vers page d'erreur avec `ErrorId` affiché. | N'expose aucune trace à la vue. |
| `JsfExceptionHandlerFactory` | Enregistre le handler JSF dans le cycle de vie Faces. | — |

---

## 4. Le catalogue d'erreurs : `ErrorCode`

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import org.springframework.http.HttpStatus;
import org.slf4j.event.Level;

/**
 * Catalogue central des erreurs — CONTRAT PUBLIC versionné.
 * Le "code" (BANK-xxxx) est stable : les tiers s'appuient dessus.
 * Le message réel est résolu via MessageSource (clé i18n), jamais ici.
 */
public enum ErrorCode {

    // 4xx — erreurs client / métier
    VALIDATION_ERROR      ("BANK-1000", HttpStatus.BAD_REQUEST,          "error.validation",        Level.WARN),
    RESOURCE_NOT_FOUND    ("BANK-1001", HttpStatus.NOT_FOUND,            "error.resource.notfound", Level.WARN),
    INSUFFICIENT_FUNDS    ("BANK-1002", HttpStatus.UNPROCESSABLE_ENTITY, "error.funds.insufficient",Level.WARN),
    ACCOUNT_BLOCKED       ("BANK-1003", HttpStatus.FORBIDDEN,            "error.account.blocked",   Level.WARN),
    DUPLICATE_OPERATION   ("BANK-1004", HttpStatus.CONFLICT,             "error.operation.duplicate",Level.WARN),
    CONCURRENT_UPDATE     ("BANK-1005", HttpStatus.CONFLICT,             "error.concurrent.update", Level.WARN),
    MALFORMED_REQUEST     ("BANK-1006", HttpStatus.BAD_REQUEST,          "error.request.malformed", Level.WARN),
    METHOD_NOT_ALLOWED    ("BANK-1007", HttpStatus.METHOD_NOT_ALLOWED,   "error.method.notallowed", Level.WARN),
    UNSUPPORTED_MEDIA_TYPE("BANK-1008", HttpStatus.UNSUPPORTED_MEDIA_TYPE,"error.media.unsupported",Level.WARN),

    // 401 / 403 — sécurité
    UNAUTHENTICATED       ("BANK-2000", HttpStatus.UNAUTHORIZED,         "error.auth.required",     Level.WARN),
    ACCESS_DENIED         ("BANK-2001", HttpStatus.FORBIDDEN,            "error.access.denied",     Level.WARN),

    // 5xx — erreurs techniques (jamais de détail exposé)
    INTERNAL_ERROR        ("BANK-9000", HttpStatus.INTERNAL_SERVER_ERROR,"error.internal",          Level.ERROR),
    SERVICE_UNAVAILABLE   ("BANK-9001", HttpStatus.SERVICE_UNAVAILABLE,  "error.service.unavailable",Level.ERROR),
    DATA_ACCESS_ERROR     ("BANK-9002", HttpStatus.INTERNAL_SERVER_ERROR,"error.internal",          Level.ERROR);

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

    public String code()            { return code; }
    public HttpStatus httpStatus()  { return httpStatus; }
    public String messageKey()      { return messageKey; }
    public Level logLevel()         { return logLevel; }
}
```

**Bonnes pratiques du catalogue**
- Le `code` public **ne change jamais** (compatibilité tiers) ; on ajoute, on ne renomme pas.
- Le HTTP status et la clé i18n peuvent évoluer indépendamment.
- Les 5xx pointent **tous** vers la même clé i18n générique `error.internal` : l'utilisateur ne voit jamais la nature technique.

---

## 5. Exceptions métier (domaine pur)

```java
package com.bank.core.domain.exception;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;

/** Base de toutes les exceptions métier. Aucune dépendance framework. */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] messageArgs; // args pour l'i18n

    protected BusinessException(ErrorCode errorCode, String devMessage, Object... messageArgs) {
        super(devMessage); // message technique DEV → uniquement pour les logs, jamais renvoyé
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }

    public ErrorCode errorCode()   { return errorCode; }
    public Object[] messageArgs()  { return messageArgs == null ? new Object[0] : messageArgs.clone(); }
}
```

```java
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s introuvable: %s".formatted(resource, id), resource, id);
    }
}

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(String iban) {
        super(ErrorCode.INSUFFICIENT_FUNDS, "Solde insuffisant sur %s".formatted(iban));
    }
}

public class AccountBlockedException extends BusinessException {
    public AccountBlockedException(String iban) {
        super(ErrorCode.ACCOUNT_BLOCKED, "Compte bloqué: %s".formatted(iban));
    }
}
```

> Le `devMessage` (super) sert **uniquement au log**. Il n'est **jamais** renvoyé au client.

---

## 6. Modèle de réponse : `ApiError`

Deux options en Spring Boot 3 :

1. **`ProblemDetail` natif (RFC 7807)** — standard, recommandé si vous voulez la conformité `application/problem+json`.
2. **DTO `ApiError` custom** — plus de contrôle sur le contrat. Retenu ici, avec une correspondance possible vers RFC 7807.

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** Réponse d'erreur EXPOSÉE. Contient uniquement des champs sûrs. Immuable. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String errorId,          // corrélation log <-> support (ex: ERR-20260724-3F9K2A7Q)
        String code,             // code public stable (ex: BANK-1002)
        int status,              // 422
        String error,            // libellé HTTP ("Unprocessable Entity")
        String message,          // message i18n SÛR destiné à l'utilisateur
        String path,             // /api/v1/accounts/123/transfer
        OffsetDateTime timestamp,
        List<FieldViolation> violations // détails de validation (champ + message), optionnel
) {
    public record FieldViolation(String field, String message) {}
}
```

**Ce que `ApiError` ne contient JAMAIS** : `exception`, `stackTrace`, `cause`, message SQL, nom de table/colonne, chemin de fichier, nom de classe Java, `getMessage()` brut d'une exception technique.

---

## 7. Génération de l'`ErrorId`

**Stratégie retenue** : identifiant **horodaté + aléatoire non séquentiel**, court et lisible au téléphone par un client au support.

Format : `ERR-yyyyMMdd-XXXXXXXX` où `XXXXXXXX` = 8 caractères Base32 (Crockford, sans I/L/O/U) tirés d'un `SecureRandom`.

- **Horodaté** : facilite le tri/recherche dans les logs et la rétention.
- **Aléatoire non séquentiel** : non devinable, pas de fuite de volumétrie.
- **Court** : dictable par téléphone, sans ambiguïté (alphabet Crockford).
- Alternative : **ULID** (26 caractères, triable, monotone) ou `UUID` — plus longs à dicter. Si vous avez déjà un tracing distribué (OpenTelemetry `traceId`), on peut le réutiliser comme `ErrorId` ; ici on garde un ID dédié, plus court.

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ErrorIdGenerator {

    // Alphabet Crockford Base32 : pas de I, L, O, U (évite les confusions à l'oral)
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final SecureRandom random = new SecureRandom();

    public String newErrorId() {
        StringBuilder sb = new StringBuilder("ERR-").append(LocalDate.now().format(DAY)).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString(); // ex: ERR-20260724-3F9K2A7Q
    }
}
```

---

## 8. Journalisation : `ErrorLogger`

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

@Component
public class ErrorLogger {

    private static final Logger log = LoggerFactory.getLogger("com.bank.errors");

    /**
     * Journalise l'erreur COMPLÈTE (stack trace incluse) côté serveur.
     * L'errorId est poussé dans le MDC pour être corrélé sur toutes les lignes de log.
     */
    public void log(String errorId, ErrorCode errorCode, String path, Throwable ex) {
        MDC.put("errorId", errorId);
        MDC.put("errorCode", errorCode.code());
        try {
            String msg = "errorId={} code={} status={} path={}";
            Object[] args = { errorId, errorCode.code(), errorCode.httpStatus().value(), path };
            if (errorCode.logLevel() == Level.ERROR) {
                // 5xx : stack trace complète en ERROR
                log.error("Technical error - " + msg, args[0], args[1], args[2], args[3], ex);
            } else {
                // 4xx métier : WARN, stack trace en DEBUG seulement (bruit maîtrisé)
                log.warn("Handled error - " + msg + " reason={}",
                        args[0], args[1], args[2], args[3], ex.getMessage());
                if (log.isDebugEnabled()) {
                    log.debug("Stack for errorId={}", errorId, ex);
                }
            }
        } finally {
            MDC.remove("errorId");
            MDC.remove("errorCode");
        }
    }
}
```

**Bonnes pratiques de journalisation**
- **MDC** : `errorId`, `errorCode`, plus idéalement `traceId`/`userId`/`requestId` alimentés par un `Filter` en amont.
- **Niveaux** : 5xx → `ERROR` avec stack trace ; 4xx métier → `WARN` sans stack (ou `DEBUG`) pour éviter le bruit.
- **Jamais** de données sensibles dans les logs (PAN complet, mot de passe, token, IBAN complet). **Masquer** (`****1234`).
- **Format structuré** (JSON via Logback `logstash-logback-encoder`) pour l'agrégation (ELK/Datadog) et la recherche par `errorId`.
- **Un seul endroit qui logue** : le handler. Interdire les `catch { log.error(...); throw; }` dispersés (double log, incohérence).
- **Rétention** : conserver assez longtemps pour couvrir le délai de réclamation client (contrainte bancaire / réglementaire).

Exemple de pattern Logback (extrait) :
```xml
<pattern>%d{ISO8601} %-5level [%X{traceId:-}] [errorId=%X{errorId:-}] %logger{36} - %msg%n</pattern>
```

---

## 9. Fabrique de réponse : `ApiErrorFactory`

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    private final MessageSource messageSource;
    private final ErrorIdGenerator errorIdGenerator;

    public ApiErrorFactory(MessageSource messageSource, ErrorIdGenerator errorIdGenerator) {
        this.messageSource = messageSource;
        this.errorIdGenerator = errorIdGenerator;
    }

    public ApiError create(ErrorCode code, String path, Locale locale,
                           Object[] msgArgs, List<ApiError.FieldViolation> violations) {
        String errorId = errorIdGenerator.newErrorId();
        String message = messageSource.getMessage(code.messageKey(), msgArgs, code.messageKey(), locale);
        return new ApiError(
                errorId,
                code.code(),
                code.httpStatus().value(),
                code.httpStatus().getReasonPhrase(),
                message,
                path,
                OffsetDateTime.now(),
                (violations == null || violations.isEmpty()) ? null : violations
        );
    }

    // Surcharge simple
    public ApiError create(ErrorCode code, String path, Locale locale) {
        return create(code, path, locale, new Object[0], null);
    }

    public String lastErrorIdFor(ApiError e) { return e.errorId(); }
}
```

> L'`errorId` renvoyé par la fabrique est **le même** que celui poussé dans les logs par `ErrorLogger` : on le génère une fois puis on le passe aux deux. (Voir handler ci-dessous : on génère l'id, on log, on construit la réponse.)

Pour garantir l'unicité de l'`errorId` entre log et réponse, la fabrique expose aussi une variante prenant un `errorId` déjà généré :

```java
public ApiError create(String errorId, ErrorCode code, String path, Locale locale,
                       Object[] msgArgs, List<ApiError.FieldViolation> violations) {
    String message = messageSource.getMessage(code.messageKey(), msgArgs, code.messageKey(), locale);
    return new ApiError(errorId, code.code(), code.httpStatus().value(),
            code.httpStatus().getReasonPhrase(), message, path, OffsetDateTime.now(),
            (violations == null || violations.isEmpty()) ? null : violations);
}
```

---

## 10. Point d'entrée REST : `GlobalRestExceptionHandler`

```java
package com.bank.core.infrastructure.adapter.in.web.error;

import com.bank.core.domain.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Point d'entrée UNIQUE pour toutes les exceptions REST.
 * Hérite de ResponseEntityExceptionHandler pour surcharger proprement
 * les exceptions Spring MVC standard (validation, routing, media type...).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

    private final ApiErrorFactory factory;
    private final ErrorLogger errorLogger;
    private final ErrorIdGenerator idGenerator;

    public GlobalRestExceptionHandler(ApiErrorFactory factory, ErrorLogger errorLogger,
                                      ErrorIdGenerator idGenerator) {
        this.factory = factory;
        this.errorLogger = errorLogger;
        this.idGenerator = idGenerator;
    }

    /* ---------- 1. Exceptions MÉTIER ---------- */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(ex.errorCode(), req, ex, ex.messageArgs(), null);
    }

    /* ---------- 2. Erreurs d'intégrité / concurrence (Hibernate/JPA) ---------- */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        // On NE renvoie JAMAIS ex.getMessage() (contient la contrainte SQL, la table, la colonne)
        return build(ErrorCode.DUPLICATE_OPERATION, req, ex, null, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest req) {
        return build(ErrorCode.CONCURRENT_UPDATE, req, ex, null, null);
    }

    /* ---------- 3. Validation par annotations sur @RequestParam/@PathVariable ---------- */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest req) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, req, ex, null, violations);
    }

    /* ---------- 4. FILET DE SÉCURITÉ : toute exception non prévue → 500 générique ---------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        return build(ErrorCode.INTERNAL_ERROR, req, ex, null, null);
    }

    /* ================= Surcharges des exceptions Spring MVC standard ================= */

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return buildObject(ErrorCode.VALIDATION_ERROR, request, ex, null, violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // JSON malformé : ne pas exposer le détail du parseur
        return buildObject(ErrorCode.MALFORMED_REQUEST, request, ex, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.METHOD_NOT_ALLOWED, request, ex, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.VALIDATION_ERROR, request, ex, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.RESOURCE_NOT_FOUND, request, ex, null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, ex, null, null);
    }

    /* ================= Méthodes utilitaires de construction ================= */

    private ResponseEntity<ApiError> build(ErrorCode code, HttpServletRequest req, Throwable ex,
                                           Object[] args, List<ApiError.FieldViolation> violations) {
        String errorId = idGenerator.newErrorId();
        String path = req.getRequestURI();
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, req.getLocale(),
                args == null ? new Object[0] : args, violations);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    // Variante pour les surcharges de ResponseEntityExceptionHandler (retour Object + WebRequest)
    private ResponseEntity<Object> buildObject(ErrorCode code, WebRequest request, Throwable ex,
                                               Object[] args, List<ApiError.FieldViolation> violations) {
        String errorId = idGenerator.newErrorId();
        String path = request.getDescription(false).replaceFirst("^uri=", "");
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, request.getLocale(),
                args == null ? new Object[0] : args, violations);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }
}
```

> **Point clé sécurité** : dans `handleAny`, `handleDataIntegrity`, `handleHttpMessageNotReadable`, on **ignore volontairement** `ex.getMessage()` pour la réponse. Le détail complet part uniquement dans les logs via `ErrorLogger`.

---

## 11. Sécurité : `AuthenticationEntryPoint` et `AccessDeniedHandler`

Ces deux composants interceptent les erreurs de sécurité **avant** que Spring MVC / les `@ControllerAdvice` ne soient atteints (elles surviennent dans la chaîne de filtres Spring Security). Il faut donc y écrire directement la réponse JSON.

```java
package com.bank.core.infrastructure.adapter.in.web.security;

import com.bank.core.infrastructure.adapter.in.web.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 — utilisateur NON authentifié tentant d'accéder à une ressource protégée. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorFactory factory;
    private final ErrorLogger errorLogger;
    private final ErrorIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ApiErrorFactory factory, ErrorLogger errorLogger,
                                        ErrorIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.factory = factory; this.errorLogger = errorLogger;
        this.idGenerator = idGenerator; this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, ErrorCode.UNAUTHENTICATED, authException,
                factory, errorLogger, idGenerator, objectMapper);
    }

    static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
                      Exception ex, ApiErrorFactory factory, ErrorLogger errorLogger,
                      ErrorIdGenerator idGenerator, ObjectMapper mapper) throws IOException {
        String errorId = idGenerator.newErrorId();
        String path = request.getRequestURI();
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, request.getLocale(), new Object[0], null);
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
```

```java
package com.bank.core.infrastructure.adapter.in.web.security;

import com.bank.core.infrastructure.adapter.in.web.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 — utilisateur authentifié mais SANS le droit requis. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorFactory factory;
    private final ErrorLogger errorLogger;
    private final ErrorIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ApiErrorFactory factory, ErrorLogger errorLogger,
                                   ErrorIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.factory = factory; this.errorLogger = errorLogger;
        this.idGenerator = idGenerator; this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        RestAuthenticationEntryPoint.write(request, response, ErrorCode.ACCESS_DENIED, ex,
                factory, errorLogger, idGenerator, objectMapper);
    }
}
```

**Câblage dans la `SecurityFilterChain`** :

```java
@Bean
SecurityFilterChain apiSecurity(HttpSecurity http,
                                RestAuthenticationEntryPoint entryPoint,
                                RestAccessDeniedHandler accessDenied) throws Exception {
    http
        .securityMatcher("/api/**")
        .authorizeHttpRequests(reg -> reg
            .requestMatchers("/api/v1/public/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(entryPoint)   // 401
            .accessDeniedHandler(accessDenied))     // 403
        .csrf(csrf -> csrf.disable())               // API stateless ; garder CSRF pour la partie JSF
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

> Prévoyez **deux** `SecurityFilterChain` distinctes (`@Order`) : une pour `/api/**` (stateless, JSON), une pour les vues JSF (session, form login, CSRF activé).

---

## 12. JSF / PrimeFaces : handler + factory

Sur le canal JSF, les exceptions non gérées remontent dans le cycle de vie Faces. On les intercepte via un `ExceptionHandlerFactory` + `ExceptionHandlerWrapper` : on log (avec `ErrorId`), puis on redirige vers une page d'erreur générique **affichant l'`ErrorId`** — jamais la trace.

```java
package com.bank.core.infrastructure.adapter.in.web.jsf;

import com.bank.core.infrastructure.adapter.in.web.error.*;
import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class JsfExceptionHandler extends ExceptionHandlerWrapper {

    private static final Logger log = LoggerFactory.getLogger(JsfExceptionHandler.class);

    public JsfExceptionHandler(ExceptionHandler wrapped) {
        super(wrapped);
    }

    @Override
    public void handle() throws FacesException {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) { super.handle(); return; }

        for (Iterator<ExceptionQueuedEvent> it = getUnhandledExceptionQueuedEvents().iterator(); it.hasNext();) {
            Throwable t = it.next().getContext().getException();
            t = unwrap(t);

            try {
                // Session expirée : rediriger vers login, pas vers page d'erreur générique
                if (t instanceof ViewExpiredException) {
                    ctx.getExternalContext().redirect(
                            ctx.getExternalContext().getRequestContextPath() + "/login.xhtml?expired=1");
                    return;
                }

                // Récupère les beans Spring depuis le contexte JSF
                var appCtx = WebApplicationContextUtils.getRequiredWebApplicationContext(
                        (jakarta.servlet.ServletContext) ctx.getExternalContext().getContext());
                ErrorIdGenerator idGen = appCtx.getBean(ErrorIdGenerator.class);
                ErrorLogger errorLogger = appCtx.getBean(ErrorLogger.class);

                String errorId = idGen.newErrorId();
                String path = requestUri(ctx);
                errorLogger.log(errorId, ErrorCode.INTERNAL_ERROR, path, t);

                // On passe UNIQUEMENT l'errorId à la vue d'erreur (jamais la trace)
                ctx.getExternalContext().getSessionMap().put("lastErrorId", errorId);
                ctx.getExternalContext().redirect(
                        ctx.getExternalContext().getRequestContextPath() + "/error.xhtml");
            } catch (Exception redirectFailure) {
                log.error("Echec de redirection vers la page d'erreur JSF", redirectFailure);
            } finally {
                it.remove();
            }
        }
        super.handle();
    }

    private Throwable unwrap(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            if (root instanceof FacesException && root.getCause() != null) { root = root.getCause(); }
            else { break; }
        }
        return root;
    }

    private String requestUri(FacesContext ctx) {
        Object req = ctx.getExternalContext().getRequest();
        return (req instanceof HttpServletRequest hsr) ? hsr.getRequestURI() : "jsf-view";
    }
}
```

```java
package com.bank.core.infrastructure.adapter.in.web.jsf;

import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;

public class JsfExceptionHandlerFactory extends ExceptionHandlerFactory {

    public JsfExceptionHandlerFactory(ExceptionHandlerFactory wrapped) {
        super(wrapped);
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        return new JsfExceptionHandler(getWrapped().getExceptionHandler());
    }
}
```

**Déclaration dans `src/main/resources/META-INF/faces-config.xml`** :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<faces-config xmlns="https://jakarta.ee/xml/ns/jakartaee"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                  https://jakarta.ee/xml/ns/jakartaee/web-facesconfig_4_0.xsd"
              version="4.0">
    <factory>
        <exception-handler-factory>
            com.bank.core.infrastructure.adapter.in.web.jsf.JsfExceptionHandlerFactory
        </exception-handler-factory>
    </factory>
</faces-config>
```

**Page `error.xhtml` (extrait)** — affiche l'`ErrorId`, jamais la trace :

```xhtml
<h:body>
  <div class="error-panel">
    <h1>Une erreur est survenue</h1>
    <p>Nos équipes ont été notifiées. Merci de communiquer la référence suivante au support :</p>
    <p class="error-id"><strong>#{sessionScope.lastErrorId}</strong></p>
  </div>
</h:body>
```

> Complément recommandé : dans `web.xml`, mapper aussi les `<error-page>` HTTP (404, 500) vers `error.xhtml` pour couvrir les erreurs hors cycle Faces (ressources statiques, filtres).

---

## 13. Exemples de réponses JSON

**Erreur métier — solde insuffisant (422)**
```json
{
  "errorId": "ERR-20260724-3F9K2A7Q",
  "code": "BANK-1002",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Le solde du compte est insuffisant pour réaliser cette opération.",
  "path": "/api/v1/accounts/FR76-XXXX/transfer",
  "timestamp": "2026-07-24T10:15:42.123+02:00"
}
```

**Erreur de validation (400) avec détails de champs**
```json
{
  "errorId": "ERR-20260724-9K2M4XPQ",
  "code": "BANK-1000",
  "status": 400,
  "error": "Bad Request",
  "message": "Les données envoyées sont invalides.",
  "path": "/api/v1/transfers",
  "timestamp": "2026-07-24T10:16:00.001+02:00",
  "violations": [
    { "field": "amount", "message": "doit être supérieur à 0" },
    { "field": "targetIban", "message": "IBAN invalide" }
  ]
}
```

**Non authentifié (401)**
```json
{
  "errorId": "ERR-20260724-77ZQ2A0B",
  "code": "BANK-2000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentification requise.",
  "path": "/api/v1/accounts",
  "timestamp": "2026-07-24T10:16:30.000+02:00"
}
```

**Erreur technique (500) — aucun détail interne**
```json
{
  "errorId": "ERR-20260724-QW3E9R7T",
  "code": "BANK-9000",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Une erreur interne est survenue. Merci de contacter le support avec la référence indiquée.",
  "path": "/api/v1/accounts/FR76-XXXX/transfer",
  "timestamp": "2026-07-24T10:17:05.500+02:00"
}
```

> Le `DataIntegrityViolationException` (contrainte SQL, ex. `uk_operation_idempotency`) est **mappé en 409 `BANK-1004`** avec un message générique ; le message SQL brut n'apparaît **que** dans les logs corrélés par `errorId`.

---

## 14. Liste des exceptions à intercepter

| Exception | Origine | ErrorCode | HTTP |
|---|---|---|---|
| `BusinessException` (et sous-classes) | Domaine | selon le code porté | 4xx |
| `MethodArgumentNotValidException` | Validation `@Valid` sur `@RequestBody` | `VALIDATION_ERROR` | 400 |
| `ConstraintViolationException` | Validation sur params / path | `VALIDATION_ERROR` | 400 |
| `MethodArgumentTypeMismatchException` | Type de param invalide | `VALIDATION_ERROR` | 400 |
| `MissingServletRequestParameterException` | Param requis manquant | `VALIDATION_ERROR` | 400 |
| `HttpMessageNotReadableException` | JSON illisible / corps manquant | `MALFORMED_REQUEST` | 400 |
| `HttpRequestMethodNotSupportedException` | Verbe HTTP non supporté | `METHOD_NOT_ALLOWED` | 405 |
| `HttpMediaTypeNotSupportedException` | `Content-Type` non supporté | `UNSUPPORTED_MEDIA_TYPE` | 415 |
| `NoResourceFoundException` / `NoHandlerFoundException` | Route inexistante | `RESOURCE_NOT_FOUND` | 404 |
| `DataIntegrityViolationException` | Contrainte SQL (Hibernate) | `DUPLICATE_OPERATION` | 409 |
| `OptimisticLockingFailureException` / `ObjectOptimisticLockingFailureException` | Concurrence JPA (`@Version`) | `CONCURRENT_UPDATE` | 409 |
| `AuthenticationException` | Filtre Spring Security | `UNAUTHENTICATED` | 401 |
| `AccessDeniedException` | Filtre Spring Security | `ACCESS_DENIED` | 403 |
| `ViewExpiredException` | JSF (session expirée) | redirection login | 302 |
| `Exception` (filet) | Tout le reste | `INTERNAL_ERROR` | 500 |

> Ne pas oublier : `DataAccessException` (parent Spring de l'accès données), `TransactionSystemException` (rollback + validation à la commit), `MaxUploadSizeExceededException` (upload), `HttpClientErrorException`/`RestClientException` (appels sortants vers d'autres services).

---

## 15. Configuration `application.yml`

**Base (`application.yml`)**
```yaml
spring:
  application:
    name: bank-core
  mvc:
    problemdetails:
      enabled: false        # on utilise notre ApiError custom
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false

server:
  error:
    include-message: never          # jamais le message d'exception dans la page d'erreur Whitelabel
    include-binding-errors: never
    include-stacktrace: never       # JAMAIS de stack trace exposée
    include-exception: false
    whitelabel:
      enabled: false                # on gère nous-mêmes toutes les erreurs

app:
  errors:
    expose-technical-details: false # flag applicatif utilisé par nos handlers
    error-page: /error.xhtml
```

**Production (`application-prod.yml`)** — verrouillage strict
```yaml
server:
  error:
    include-message: never
    include-stacktrace: never
    include-exception: false
    include-binding-errors: never

app:
  errors:
    expose-technical-details: false

logging:
  level:
    root: INFO
    com.bank: INFO
    com.bank.errors: INFO
    org.hibernate.SQL: WARN
    org.springframework.web: WARN
  config: classpath:logback-spring.xml   # sortie JSON structurée pour l'agrégation
```

**Développement (`application-dev.yml`)** — confort local (jamais en prod)
```yaml
server:
  error:
    include-message: always
    include-stacktrace: on_param        # ?trace=true en local uniquement
    include-binding-errors: always

app:
  errors:
    expose-technical-details: true      # utilisé par nos handlers pour enrichir la réponse EN DEV

logging:
  level:
    com.bank: DEBUG
    org.hibernate.SQL: DEBUG
```

> Le flag `app.errors.expose-technical-details` est injecté dans `ApiErrorFactory` (`@Value` ou `@ConfigurationProperties`). En dev, on peut ajouter un champ `debug` (message technique) à `ApiError` **conditionnellement** ; en prod, il est **toujours** `false` et le champ n'est jamais peuplé.

---

## 16. Bonnes pratiques de journalisation (synthèse)

1. **Un seul point de log** : le handler. Pas de `log.error` dispersés qui doublonnent.
2. **MDC systématique** : `errorId`, `traceId`, `userId` (masqué), `requestId`. Nettoyage en `finally`.
3. **Niveaux cohérents** : 5xx → `ERROR` + stack ; 4xx métier → `WARN` sans stack ; 401/403 → `WARN`.
4. **Logs structurés JSON** (`logstash-logback-encoder`) pour la recherche par `errorId` dans ELK/Datadog/Splunk.
5. **Masquage des données sensibles** : jamais de PAN, CVV, mot de passe, token, IBAN complet, PII en clair. Utiliser des converters de masquage.
6. **Corrélation bout-en-bout** : propager le `traceId` (OpenTelemetry / Micrometer Tracing) sur tous les services.
7. **Rétention conforme** : durée alignée sur les obligations bancaires et le délai de réclamation client.
8. **Alerting** : seuil d'alerte sur le taux de 5xx par `errorCode`.
9. **Ne jamais logguer le corps complet** d'une requête bancaire sensible (virement) sans masquage.
10. **Idempotence du log** : même `errorId` dans le log **et** dans la réponse → recherche immédiate.

---

## 17. Stratégie de tests

### 17.1 Tests unitaires

**`ErrorIdGenerator`**
```java
class ErrorIdGeneratorTest {

    private final ErrorIdGenerator gen = new ErrorIdGenerator();

    @Test
    void genere_un_id_au_format_attendu() {
        String id = gen.newErrorId();
        assertThat(id).matches("ERR-\\d{8}-[0-9A-HJKMNP-TV-Z]{8}");
    }

    @Test
    void genere_des_ids_uniques() {
        Set<String> ids = IntStream.range(0, 10_000)
                .mapToObj(i -> gen.newErrorId()).collect(Collectors.toSet());
        assertThat(ids).hasSize(10_000); // pas de collision
    }

    @Test
    void n_utilise_pas_de_caracteres_ambigus() {
        String suffix = gen.newErrorId().substring(13);
        assertThat(suffix).doesNotContainAnyWhitespaces();
        assertThat(suffix).matches("[^ILOU]+"); // alphabet Crockford
    }
}
```

**`ApiErrorFactory`** (avec `MessageSource` mocké)
```java
@Test
void resout_le_message_i18n_et_ne_fuit_pas_l_exception() {
    var messageSource = mock(MessageSource.class);
    when(messageSource.getMessage(eq("error.funds.insufficient"), any(), any(), any()))
            .thenReturn("Solde insuffisant.");
    var factory = new ApiErrorFactory(messageSource, new ErrorIdGenerator());

    ApiError err = factory.create(ErrorCode.INSUFFICIENT_FUNDS, "/api/v1/transfer", Locale.FRENCH);

    assertThat(err.message()).isEqualTo("Solde insuffisant.");
    assertThat(err.code()).isEqualTo("BANK-1002");
    assertThat(err.status()).isEqualTo(422);
    assertThat(err.errorId()).startsWith("ERR-");
    // Le record ne contient AUCUN champ technique
    assertThat(ApiError.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("stackTrace", "exception", "cause");
}
```

### 17.2 Tests d'intégration Web (`@WebMvcTest` + MockMvc)

```java
@WebMvcTest(controllers = TransferController.class)
@Import({GlobalRestExceptionHandler.class, ApiErrorFactory.class,
         ErrorIdGenerator.class, ErrorLogger.class})
class GlobalRestExceptionHandlerIT {

    @Autowired MockMvc mvc;
    @MockBean TransferUseCase transferUseCase; // port applicatif

    @Test
    void business_exception_est_mappee_proprement() throws Exception {
        when(transferUseCase.execute(any()))
                .thenThrow(new InsufficientFundsException("FR76XXXX"));

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"sourceIban":"FR76AAAA","targetIban":"FR76BBBB","amount":100}
                        """))
           .andExpect(status().isUnprocessableEntity())
           .andExpect(jsonPath("$.code").value("BANK-1002"))
           .andExpect(jsonPath("$.status").value(422))
           .andExpect(jsonPath("$.errorId", startsWith("ERR-")))
           // AUCUNE fuite technique
           .andExpect(jsonPath("$.stackTrace").doesNotExist())
           .andExpect(jsonPath("$.exception").doesNotExist())
           .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void exception_technique_devient_500_generique_sans_detail() throws Exception {
        when(transferUseCase.execute(any()))
                .thenThrow(new IllegalStateException("connexion JDBC fermée: ORA-03113"));

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("BANK-9000"))
           // Le message ORA-xxxx NE DOIT PAS apparaître
           .andExpect(jsonPath("$.message").value(not(containsString("ORA-"))))
           .andExpect(jsonPath("$.errorId", startsWith("ERR-")));
    }

    @Test
    void validation_retourne_400_avec_violations() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"sourceIban":"","targetIban":"BAD","amount":-5}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("BANK-1000"))
           .andExpect(jsonPath("$.violations").isArray());
    }
}
```

### 17.3 Tests d'intégration Sécurité (`@SpringBootTest` + Spring Security Test)

```java
@Test @WithAnonymousUser
void non_authentifie_recoit_401_json_generique() throws Exception {
    mvc.perform(get("/api/v1/accounts"))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.code").value("BANK-2000"))
       .andExpect(jsonPath("$.errorId", startsWith("ERR-")))
       .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
}

@Test @WithMockUser(roles = "CLIENT")
void authentifie_sans_droit_recoit_403_json() throws Exception {
    mvc.perform(delete("/api/v1/admin/accounts/1").with(csrf()))
       .andExpect(status().isForbidden())
       .andExpect(jsonPath("$.code").value("BANK-2001"));
}
```

### 17.4 Test « anti-fuite » transversal (garde-fou)

Test paramétré qui, pour un panel d'exceptions, vérifie que **jamais** la réponse ne contient de motifs interdits :
```java
@ParameterizedTest
@MethodSource("exceptionsProvider")
void aucune_reponse_ne_contient_de_detail_technique(Throwable thrown) throws Exception {
    when(useCase.execute(any())).thenThrow(thrown);
    var body = mvc.perform(post("/api/v1/transfers")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContainPattern("(?i)(ORA-|SQLException|at com\\.|Caused by|\\.java:|nested exception)");
}
```

### 17.5 Ce qu'il faut couvrir — check-list de tests
- [ ] Chaque `ErrorCode` produit le bon HTTP status.
- [ ] `errorId` présent dans **toutes** les réponses d'erreur.
- [ ] `errorId` de la réponse == `errorId` du log (test avec appender de capture Logback).
- [ ] Aucune réponse ne contient stack trace / SQL / nom de classe.
- [ ] Messages i18n résolus (FR/EN) selon `Accept-Language`.
- [ ] 401 vs 403 correctement distingués.
- [ ] JSF : `ViewExpiredException` → redirection login ; autre → `error.xhtml` avec `lastErrorId`.
- [ ] Validation → liste `violations` correcte.

---

## 18. Récapitulatif des flux

```
                 ┌─────────────────────────────────────────────┐
   REST tiers ──►│ SecurityFilterChain                          │
                 │   ├─ non authentifié ─► RestAuthEntryPoint ──┼─► 401 ApiError JSON
                 │   └─ non autorisé   ─► RestAccessDeniedH. ───┼─► 403 ApiError JSON
                 │ DispatcherServlet ─► Controller ─► UseCase   │
                 │        │ (BusinessException / technique)     │
                 │        ▼                                      │
                 │  GlobalRestExceptionHandler (@RestCtrlAdvice) │
                 │    ├─ ErrorIdGenerator  (génère ERR-...)      │
                 │    ├─ ErrorLogger       (log complet + MDC)   │
                 │    └─ ApiErrorFactory   (JSON sûr + i18n) ────┼─► 4xx/5xx ApiError JSON
                 └─────────────────────────────────────────────┘

                 ┌─────────────────────────────────────────────┐
   UI PrimeFaces►│ Faces lifecycle ─► JsfExceptionHandler       │
                 │   ├─ ViewExpired ─► redirect /login          │
                 │   └─ autre ─► log(ErrorId) ─► redirect        │
                 │                 /error.xhtml (#{lastErrorId}) │
                 └─────────────────────────────────────────────┘
```

**Invariant central** : quel que soit le canal, l'utilisateur ne reçoit qu'un **message générique + un `ErrorId`**, et le serveur conserve la **trace complète corrélée** par ce même `ErrorId`.
