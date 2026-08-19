package com.auditlog.exception;

import com.auditlog.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Validation failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                        "Malformed request body", List.of("Request body could not be parsed")));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleBadDateTime(DateTimeParseException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                        "Invalid date/time parameter", List.of(ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.NOT_FOUND.value(), "Not found", List.of(ex.getMessage())));
    }

    @ExceptionHandler(com.auditlog.security.UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(com.auditlog.security.UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.UNAUTHORIZED.value(), "Unauthorized", List.of(ex.getMessage())));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.FORBIDDEN.value(), "Forbidden", List.of(ex.getMessage())));
    }
}
