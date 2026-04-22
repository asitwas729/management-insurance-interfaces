package com.example.interfacehub.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(new ErrorResponse(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        return ResponseEntity
            .status(ErrorCode.INVALID_REQUEST.getStatus())
            .body(new ErrorResponse(ErrorCode.INVALID_REQUEST.name(), message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity
            .status(ErrorCode.UNAUTHORIZED.getStatus())
            .body(new ErrorResponse(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage()));
    }
}
