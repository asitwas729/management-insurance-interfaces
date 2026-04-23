package com.example.interfacehub.common.error;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(new ErrorResponse(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .collect(Collectors.toList());

        String message = errors.isEmpty() ? ErrorCode.INVALID_REQUEST.getMessage() 
            : errors.get(0).field() + " " + errors.get(0).message();

        return ResponseEntity
            .status(ErrorCode.INVALID_REQUEST.getStatus())
            .body(new ErrorResponse(ErrorCode.INVALID_REQUEST.name(), message, errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity
            .status(ErrorCode.UNAUTHORIZED.getStatus())
            .body(new ErrorResponse(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRequestNotPermitted(RequestNotPermitted exception) {
        log.warn("Rate limit exceeded: {}", exception.getMessage());
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("TOO_MANY_REQUESTS", "Too many login attempts. Please try again later."));
    }
}
