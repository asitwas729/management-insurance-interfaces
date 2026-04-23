package com.example.interfacehub.presentation;

import com.example.interfacehub.application.standard.StandardContractService;
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
public class StandardContractController {

    private final StandardContractService standardContractService;

    public StandardContractController(StandardContractService standardContractService) {
        this.standardContractService = standardContractService;
    }

    @GetMapping("/error-codes")
    public List<ErrorCatalogResponse> findErrorCatalogs() {
        return standardContractService.findErrorCatalogs().stream()
            .map(ErrorCatalogResponse::from)
            .toList();
    }

    @GetMapping("/reprocess-policies")
    public List<ReprocessPolicyResponse> findReprocessPolicies() {
        return standardContractService.findReprocessPolicies().stream()
            .map(ReprocessPolicyResponse::from)
            .toList();
    }

    @PutMapping("/reprocess-policies/{errorCode}")
    public ReprocessPolicyResponse upsertReprocessPolicy(
        @PathVariable String errorCode,
        @Valid @RequestBody UpsertReprocessPolicyRequest request
    ) {
        return ReprocessPolicyResponse.from(standardContractService.upsertReprocessPolicy(errorCode, request));
    }

    @GetMapping("/maintenance-windows")
    public List<MaintenanceWindowResponse> findMaintenanceWindows(@RequestParam(required = false) String externalOrg) {
        return standardContractService.findMaintenanceWindows(externalOrg).stream()
            .map(MaintenanceWindowResponse::from)
            .toList();
    }

    @PostMapping("/maintenance-windows")
    public MaintenanceWindowResponse createMaintenanceWindow(
        @Valid @RequestBody CreateMaintenanceWindowRequest request
    ) {
        return MaintenanceWindowResponse.from(standardContractService.createMaintenanceWindow(request));
    }

    @DeleteMapping("/maintenance-windows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaintenanceWindow(@PathVariable Long id) {
        standardContractService.deleteMaintenanceWindow(id);
    }
}
