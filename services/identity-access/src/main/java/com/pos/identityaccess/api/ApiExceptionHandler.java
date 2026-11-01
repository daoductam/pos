package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ErrorBody;
import com.pos.identityaccess.api.dto.ErrorEnvelope;
import com.pos.identityaccess.api.dto.ApiMeta;
import com.pos.identityaccess.application.exception.IdentityAccessException;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public final class ApiExceptionHandler {

    private final SecurityMetrics metrics;

    public ApiExceptionHandler() {
        this(SecurityMetrics.noop());
    }

    @Autowired
    public ApiExceptionHandler(SecurityMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(IdentityAccessException.class)
    ResponseEntity<ErrorEnvelope> handleIdentity(
            IdentityAccessException exception, HttpServletRequest request) {
        if (exception.status() == 409) {
            metrics.recordConflict();
        }
        if ("cognito_unavailable".equals(exception.code())) {
            metrics.recordCognitoError();
        }
        return ResponseEntity.status(exception.status()).body(error(exception.code(), safeMessage(exception), request));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErrorEnvelope> handleDataConflict(
            org.springframework.dao.DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(409)
                .body(error("conflict", "The requested state conflicts with existing data", request));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorEnvelope> handleValidation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(422)
                .body(error("validation_error", "Request is invalid", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(error("internal_error", "Unexpected error", request));
    }

    private static ErrorEnvelope error(String code, String message, HttpServletRequest request) {
        ApiMeta meta = RequestContext.meta(request);
        return new ErrorEnvelope(new ErrorBody(code, message, List.of(), meta.requestId(), meta.correlationId()));
    }

    private static String safeMessage(IdentityAccessException exception) {
        return switch (exception.code()) {
            case "validation_error" -> "Request is invalid";
            case "forbidden" -> "The caller is not allowed to access this resource";
            case "not_found" -> "The resource does not exist in the authorized scope";
            case "cognito_unavailable" -> "The identity provider is temporarily unavailable";
            case "rate_limited" -> "Too many requests";
            default -> "The request could not be completed";
        };
    }
}
