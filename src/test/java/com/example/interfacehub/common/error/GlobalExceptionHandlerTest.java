package com.example.interfacehub.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationException_returns_multiple_errors() throws Exception {
        // Given
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "field1", "error1"));
        bindingResult.addError(new FieldError("target", "field2", "error2"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // When
        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_REQUEST.name(), response.getBody().code());
        
        List<ErrorResponse.FieldErrorDetail> fieldErrors = response.getBody().fieldErrors();
        assertEquals(2, fieldErrors.size());
        assertEquals("field1", fieldErrors.get(0).field());
        assertEquals("error1", fieldErrors.get(0).message());
        assertEquals("field2", fieldErrors.get(1).field());
        assertEquals("error2", fieldErrors.get(1).message());
    }
}
