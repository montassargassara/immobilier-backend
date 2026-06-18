package com.immobilier.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> buildResponse(Map<String, Object> body, HttpStatus status) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private Map<String, Object> errorBody(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        body.put("status", status.value());
        return body;
    }

    // ── Validation errors (400) ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return buildResponse(errorBody(message, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(errorBody(ex.getMessage(), HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    // ── Auth errors ───────────────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(errorBody("Email ou mot de passe incorrect", HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(errorBody("Accès non autorisé", HttpStatus.FORBIDDEN), HttpStatus.FORBIDDEN);
    }

    // ── Unexpected runtime errors (500) ──────────────────────────────────────
    // Infrastructure errors (DB, IO, etc.) must never be reported as 400.
    // Business-validation exceptions that should be 400 have their own handlers
    // above (IllegalArgumentException). A bare RuntimeException reaching here
    // means something unexpected happened on the server side.

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("[GlobalExceptionHandler] RuntimeException — class={} message={}",
                ex.getClass().getName(), ex.getMessage(), ex);
        return buildResponse(errorBody("Erreur serveur interne: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Unexpected checked errors (500) ───────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("[GlobalExceptionHandler] Exception — class={} message={}",
                ex.getClass().getName(), ex.getMessage(), ex);
        return buildResponse(errorBody(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
