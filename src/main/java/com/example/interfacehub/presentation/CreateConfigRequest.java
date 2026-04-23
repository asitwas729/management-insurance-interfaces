package com.example.interfacehub.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record CreateConfigRequest(
    @NotBlank
    @Pattern(
        regexp = "^(https?|ftp|sftp)://[\\w.-]+(:\\d+)?(/\\S*)?$",
        message = "endpoint must be a valid URL (http, https, ftp, sftp)"
    )
    String endpoint,

    String authType,
    Map<String, String> headers,

    @NotNull @Positive Long timeoutMillis,

    boolean sandboxMode,

    @Min(value = 100, message = "mockHttpStatus must be between 100 and 599")
    @Max(value = 599, message = "mockHttpStatus must be between 100 and 599")
    Integer mockHttpStatus,

    String mockResponseBody
) {
}
