package com.example.interfacehub.presentation;

import com.example.interfacehub.application.registry.ResiliencePolicyService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.presentation.request.UpsertResiliencePolicyRequest;
import com.example.interfacehub.presentation.response.ResiliencePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/resilience-policy")
@Tag(name = "Resilience Policy", description = "Per-interface Resilience4j policy management")
public class ResiliencePolicyController {

    private final ResiliencePolicyService resiliencePolicyService;

    public ResiliencePolicyController(ResiliencePolicyService resiliencePolicyService) {
        this.resiliencePolicyService = resiliencePolicyService;
    }

    @GetMapping
    @Operation(summary = "Get resilience policy for interface")
    public ResiliencePolicyResponse get(@PathVariable String interfaceCode) {
        return resiliencePolicyService.findByInterfaceCode(interfaceCode)
            .map(ResiliencePolicyResponse::from)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.IF_NOT_FOUND,
                "No resilience policy for: " + interfaceCode
            ));
    }

    @PutMapping
    @Operation(summary = "Upsert resilience policy for interface")
    public ResiliencePolicyResponse upsert(
        @PathVariable String interfaceCode,
        @Valid @RequestBody UpsertResiliencePolicyRequest request
    ) {
        return ResiliencePolicyResponse.from(resiliencePolicyService.upsert(interfaceCode, request));
    }
}
