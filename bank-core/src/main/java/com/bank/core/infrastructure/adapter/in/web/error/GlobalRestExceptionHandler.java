package com.bank.core.infrastructure.adapter.in.web.error;

import com.bank.core.domain.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;

/**
 * Point d'entrée UNIQUE pour toutes les exceptions REST.
 * <p>
 * Hérite de {@link ResponseEntityExceptionHandler} pour surcharger proprement
 * les exceptions Spring MVC standard, et ajoute la gestion des exceptions
 * métier, d'accès aux données et un filet de sécurité générique.
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

    /* ---------- 2. Intégrité / concurrence (Hibernate / JPA) ---------- */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        // On NE renvoie JAMAIS ex.getMessage() (contrainte SQL, table, colonne).
        return build(ErrorCode.DUPLICATE_OPERATION, req, ex, null, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest req) {
        return build(ErrorCode.CONCURRENT_UPDATE, req, ex, null, null);
    }

    /* ---------- 3. Validation sur @RequestParam / @PathVariable ---------- */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest req) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, req, ex, null, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, ex, null, null);
    }

    /* ---------- 4. FILET DE SÉCURITÉ : toute exception non prévue -> 500 générique ---------- */
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
        return buildObject(ErrorCode.VALIDATION_ERROR, request, ex, violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.MALFORMED_REQUEST, request, ex, null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.METHOD_NOT_ALLOWED, request, ex, null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request, ex, null);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.VALIDATION_ERROR, request, ex, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return buildObject(ErrorCode.RESOURCE_NOT_FOUND, request, ex, null);
    }

    /* ================= Construction ================= */

    private ResponseEntity<ApiError> build(ErrorCode code, HttpServletRequest req, Throwable ex,
                                           Object[] args, List<ApiError.FieldViolation> violations) {
        String errorId = idGenerator.newErrorId();
        String path = req.getRequestURI();
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, req.getLocale(),
                args == null ? new Object[0] : args, violations, ex);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    private ResponseEntity<Object> buildObject(ErrorCode code, WebRequest request, Throwable ex,
                                               List<ApiError.FieldViolation> violations) {
        String errorId = idGenerator.newErrorId();
        String path = request.getDescription(false).replaceFirst("^uri=", "");
        Locale locale = request.getLocale() == null ? Locale.getDefault() : request.getLocale();
        errorLogger.log(errorId, code, path, ex);
        ApiError body = factory.create(errorId, code, path, locale, new Object[0], violations, ex);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }
}
