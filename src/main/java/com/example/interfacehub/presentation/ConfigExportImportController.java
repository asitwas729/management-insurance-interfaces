package com.example.interfacehub.presentation;

import com.example.interfacehub.application.admin.ConfigExportService;
import com.example.interfacehub.application.admin.ConfigImportService;
import com.example.interfacehub.presentation.admin.ExportPayload;
import com.example.interfacehub.presentation.admin.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Config export / import APIs")
public class ConfigExportImportController {

    private final ConfigExportService exportService;
    private final ConfigImportService importService;

    public ConfigExportImportController(ConfigExportService exportService, ConfigImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export all interface configs")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export succeeded"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public ResponseEntity<ExportPayload> export(Authentication authentication) {
        String actor = authentication == null ? "system" : authentication.getName();
        ExportPayload payload = exportService.exportAll(actor);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interface-config-export.json\"")
            .body(payload);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Import interface configs (upsert)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Import succeeded"),
        @ApiResponse(responseCode = "400", description = "Invalid payload"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public ImportResult importConfig(@RequestBody ExportPayload payload, Authentication authentication) {
        String actor = authentication == null ? "system" : authentication.getName();
        return importService.importAll(payload, actor);
    }
}

