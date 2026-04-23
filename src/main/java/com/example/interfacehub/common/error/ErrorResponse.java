package com.example.interfacehub.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<FieldErrorDetail> fieldErrors
) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
