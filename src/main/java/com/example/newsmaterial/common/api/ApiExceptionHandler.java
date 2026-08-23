package com.example.newsmaterial.common.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(body(exception.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> bodyValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> error.getField() + ": " + message(error.getDefaultMessage()))
                .toList();
        if (details.isEmpty()) {
            details = exception.getBindingResult().getGlobalErrors().stream()
                    .map(error -> error.getObjectName() + ": " + message(error.getDefaultMessage()))
                    .toList();
        }
        return validation(details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorBody> methodValidation(HandlerMethodValidationException exception) {
        List<String> details = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            if (name == null || name.isBlank()) {
                name = "parameter";
            }
            String parameterName = name;
            result.getResolvableErrors().forEach(error ->
                    details.add(parameterName + ": " + message(error.getDefaultMessage())));
        });
        return validation(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> constraint(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    int separator = path.lastIndexOf('.');
                    String field = separator >= 0 ? path.substring(separator + 1) : path;
                    return field + ": " + violation.getMessage();
                })
                .sorted()
                .toList();
        return validation(details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(body("INVALID_REQUEST", "Request body is invalid",
                List.of("body: Malformed JSON or invalid field value")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorBody> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return validation(List.of(exception.getName() + ": Invalid value"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorBody> missingHeader(MissingRequestHeaderException exception) {
        return validation(List.of(exception.getHeaderName() + ": Header is required"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorBody> missingParameter(MissingServletRequestParameterException exception) {
        return validation(List.of(exception.getParameterName() + ": Parameter is required"));
    }

    private ResponseEntity<ErrorBody> validation(List<String> details) {
        List<String> safeDetails = details == null || details.isEmpty()
                ? List.of("request: Request validation failed")
                : List.copyOf(details);
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", "Request validation failed", safeDetails));
    }

    private ErrorBody body(String code, String message, List<String> details) {
        return new ErrorBody(code, message, UUID.randomUUID().toString(), List.copyOf(details));
    }

    private String message(String value) {
        return value == null || value.isBlank() ? "Invalid value" : value;
    }
}
