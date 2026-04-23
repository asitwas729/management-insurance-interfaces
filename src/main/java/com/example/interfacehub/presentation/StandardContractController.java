package com.example.interfacehub.presentation;

import com.example.interfacehub.application.standard.StandardContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/standards")
@Tag(name = "Standard Contracts", description = "Error code, policy and maintenance standard APIs")
public class StandardContractController {

    private final StandardContractService standardContractService;

    public StandardContractController(StandardContractService standardContractService) {
        this.standardContractService = standardContractService;
    }

    @GetMapping("/error-codes")
    @Operation(summary = "List error catalogs", description = "Returns standard error code catalog")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Error catalogs returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public List<ErrorCatalogResponse> findErrorCatalogs() {
        return standardContractService.findErrorCatalogs().stream()
            .map(ErrorCatalogResponse::from)
            .toList();
    }

    @GetMapping("/reprocess-policies")
    @Operation(summary = "List reprocess policies", description = "Returns all reprocess policies")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policies returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public List<ReprocessPolicyResponse> findReprocessPolicies() {
        return standardContractService.findReprocessPolicies().stream()
            .map(ReprocessPolicyResponse::from)
            .toList();
    }

    @PutMapping("/reprocess-policies/{errorCode}")
    @Operation(summary = "Upsert reprocess policy", description = "Creates or updates policy by error code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy upserted"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ReprocessPolicyResponse upsertReprocessPolicy(
        @PathVariable String errorCode,
        @Valid @RequestBody UpsertReprocessPolicyRequest request
    ) {
        return ReprocessPolicyResponse.from(standardContractService.upsertReprocessPolicy(errorCode, request));
    }

    @GetMapping("/maintenance-windows")
    @Operation(summary = "List maintenance windows", description = "Returns maintenance windows, optionally filtered by external org")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Maintenance windows returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public List<MaintenanceWindowResponse> findMaintenanceWindows(@RequestParam(required = false) String externalOrg) {
        return standardContractService.findMaintenanceWindows(externalOrg).stream()
            .map(MaintenanceWindowResponse::from)
            .toList();
    }

    @PostMapping("/maintenance-windows")
    @Operation(summary = "Create maintenance window", description = "Creates a maintenance window")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Maintenance window created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public MaintenanceWindowResponse createMaintenanceWindow(
        @Valid @RequestBody CreateMaintenanceWindowRequest request
    ) {
        return MaintenanceWindowResponse.from(standardContractService.createMaintenanceWindow(request));
    }

    @DeleteMapping("/maintenance-windows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete maintenance window", description = "Deletes maintenance window by id")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Maintenance window deleted"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Maintenance window not found")
    })
    public void deleteMaintenanceWindow(@PathVariable Long id) {
        standardContractService.deleteMaintenanceWindow(id);
    }
}
