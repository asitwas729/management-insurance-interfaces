package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.application.standardmessage.StandardMessageAdminService;
import com.example.interfacehub.application.standardmessage.StandardMessageValidationService;
import com.example.interfacehub.presentation.admin.StandardMessageValidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/standard-message-schemas")
@Tag(name = "Admin", description = "Standard message schema (XSD) and rule management")
public class StandardMessageAdminController {

    private final StandardMessageAdminService adminService;
    private final StandardMessageValidationService validationService;

    public StandardMessageAdminController(
        StandardMessageAdminService adminService,
        StandardMessageValidationService validationService
    ) {
        this.adminService = adminService;
        this.validationService = validationService;
    }

    @PutMapping("/{schemaCode}/versions/{version}")
    @Operation(summary = "Upsert XSD schema")
    public StandardMessageSchemaResponse upsertSchema(
        @PathVariable String schemaCode,
        @PathVariable int version,
        @Valid @RequestBody UpsertStandardMessageSchemaRequest request
    ) {
        return StandardMessageSchemaResponse.from(
            adminService.upsertSchema(schemaCode, version, request.xsdText(), request.enabled())
        );
    }

    @GetMapping("/{schemaCode}/versions/{version}")
    @Operation(summary = "Get XSD schema")
    public StandardMessageSchemaResponse getSchema(@PathVariable String schemaCode, @PathVariable int version) {
        return StandardMessageSchemaResponse.from(adminService.getSchema(schemaCode, version));
    }

    @PutMapping("/{schemaCode}/versions/{version}/rules/{ruleId}")
    @Operation(summary = "Upsert validation rule")
    public StandardMessageRuleResponse upsertRule(
        @PathVariable String schemaCode,
        @PathVariable int version,
        @PathVariable String ruleId,
        @Valid @RequestBody UpsertStandardMessageRuleRequest request
    ) {
        return StandardMessageRuleResponse.from(adminService.upsertRule(
            schemaCode,
            version,
            ruleId,
            request.severity(),
            request.xpathExpr(),
            request.operator(),
            request.expectedValue(),
            request.message(),
            request.enabled()
        ));
    }

    @GetMapping("/{schemaCode}/versions/{version}/rules")
    @Operation(summary = "List rules")
    public List<StandardMessageRuleResponse> listRules(@PathVariable String schemaCode, @PathVariable int version) {
        return adminService.listRules(schemaCode, version).stream()
            .map(StandardMessageRuleResponse::from)
            .toList();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate sample XML against XSD + rules")
    public StandardMessageValidateResponse validate(@Valid @RequestBody StandardMessageValidateRequest request) {
        return StandardMessageValidateResponse.from(validationService.validate(
            request.schemaCode(),
            request.version(),
            request.xml(),
            request.applyRules()
        ));
    }
}

