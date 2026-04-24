package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/execute")
@Tag(name = "Execution", description = "Manual interface execution APIs")
public class ExecutionController {

    private final ExecutionOrchestrator executionOrchestrator;

    public ExecutionController(ExecutionOrchestrator executionOrchestrator) {
        this.executionOrchestrator = executionOrchestrator;
    }

    @PostMapping
    @Operation(summary = "Execute interface manually", description = "Triggers manual execution for a given interface")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution started"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public ExecutionResponse executeManually(
        @PathVariable String interfaceCode,
        @Valid @RequestBody ExecuteInterfaceRequest request,
        HttpServletRequest httpRequest,
        Authentication authentication
    ) {
        PolicyExecutionContext context = new PolicyExecutionContext(
            request.clientId(),
            request.clientSecret(),
            request.apiKey(),
            resolveAuthHeader(httpRequest),
            request.partnerId(),
            httpRequest.getRemoteAddr(),
            extractRoles(authentication)
        );
        return ExecutionResponse.from(executionOrchestrator.executeManually(interfaceCode, request, context));
    }

    private String resolveAuthHeader(HttpServletRequest request) {
        String mtlsHeader = request.getHeader("X-mTLS-Verified");
        if (mtlsHeader != null) {
            return mtlsHeader;
        }
        return request.getHeader("Authorization");
    }

    private Set<String> extractRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .collect(Collectors.toSet());
    }
}
