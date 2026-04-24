package com.example.interfacehub.presentation;

import com.example.interfacehub.application.policy.PolicyManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Policy", description = "Interface policy and security template APIs")
public class PolicyController {

    private final PolicyManagementService policyManagementService;
    private final ObjectMapper objectMapper;

    public PolicyController(PolicyManagementService policyManagementService, ObjectMapper objectMapper) {
        this.policyManagementService = policyManagementService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/policies/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create policy template")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Template created"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public PolicyTemplateResponse createTemplate(
        @Valid @RequestBody CreatePolicyTemplateRequest request,
        @RequestParam(defaultValue = "system") String actor
    ) {
        return PolicyTemplateResponse.from(policyManagementService.createTemplate(request, actor), objectMapper);
    }

    @PatchMapping("/policies/templates/{policyName}")
    @Operation(summary = "Update policy template")
    public PolicyTemplateResponse updateTemplate(
        @PathVariable String policyName,
        @Valid @RequestBody UpdatePolicyTemplateRequest request,
        @RequestParam(defaultValue = "system") String actor
    ) {
        return PolicyTemplateResponse.from(policyManagementService.updateTemplate(policyName, request, actor), objectMapper);
    }

    @GetMapping("/policies/templates")
    @Operation(summary = "List policy templates")
    public List<PolicyTemplateResponse> findTemplates() {
        return policyManagementService.findTemplates().stream()
            .map(template -> PolicyTemplateResponse.from(template, objectMapper))
            .toList();
    }

    @PostMapping("/interfaces/{interfaceCode}/policy-bindings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bind policy template to interface")
    public InterfacePolicyBindingResponse bindPolicy(
        @PathVariable String interfaceCode,
        @Valid @RequestBody BindInterfacePolicyRequest request,
        @RequestParam(defaultValue = "system") String actor
    ) {
        return InterfacePolicyBindingResponse.from(policyManagementService.bindPolicy(interfaceCode, request, actor));
    }

    @GetMapping("/interfaces/{interfaceCode}/policy-bindings")
    @Operation(summary = "List policy bindings for interface")
    public List<InterfacePolicyBindingResponse> findBindings(@PathVariable String interfaceCode) {
        return policyManagementService.findBindings(interfaceCode).stream()
            .map(InterfacePolicyBindingResponse::from)
            .toList();
    }
}
