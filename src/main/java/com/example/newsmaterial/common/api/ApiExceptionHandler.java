package com.example.newsmaterial.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorBody(exception.code(), exception.getMessage(), UUID.randomUUID().toString(), List.of()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorBody> validation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_ERROR", "Request validation failed",
                        UUID.randomUUID().toString(), List.of("title is required")));
    }
}
