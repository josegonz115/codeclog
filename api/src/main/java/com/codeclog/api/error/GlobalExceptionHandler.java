package com.codeclog.api.error;

import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the one error envelope. Extending {@link ResponseEntityExceptionHandler}
 * means Spring MVC's own exceptions (unreadable body, wrong method, no handler) are routed through
 * here too, so there is no second, accidental error shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        if (ex.code().status().is5xxServerError()) {
            log.error("API error {}", ex.code(), ex);
        } else {
            log.debug("API error {}: {}", ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.code().status())
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details()));
    }

    /** Validation on {@code @RequestParam} / {@code @PathVariable} constraints. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorResponse.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.Detail(
                        v.getPropertyPath() == null ? null : v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(d -> String.valueOf(d.field())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", details);
    }

    /** Last resort. The message is deliberately generic: internals do not leak to clients. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Something went wrong on our end", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiErrorResponse.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorResponse.Detail(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiErrorResponse.Detail::field))
                .toList();
        return asObject(respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", details));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiErrorResponse.Detail> details = ex.getAllErrors().stream()
                .map(e -> new ApiErrorResponse.Detail(null, e.getDefaultMessage()))
                .toList();
        return asObject(respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return asObject(respond(ErrorCode.MALFORMED_REQUEST, "Request body could not be parsed", List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return asObject(respond(
                ErrorCode.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported here", List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return asObject(respond(ErrorCode.NOT_FOUND, "No endpoint at /" + ex.getResourcePath(), List.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                List.of(new ApiErrorResponse.Detail(ex.getName(), "Value is not a valid " + simpleTypeName(ex))));
    }

    private static String simpleTypeName(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        return required == null ? "value" : required.getSimpleName();
    }

    private static ResponseEntity<ApiErrorResponse> respond(
            ErrorCode code, String message, List<ApiErrorResponse.Detail> details) {
        HttpStatus status = code.status();
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, details));
    }

    private static ResponseEntity<Object> asObject(ResponseEntity<ApiErrorResponse> response) {
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}
