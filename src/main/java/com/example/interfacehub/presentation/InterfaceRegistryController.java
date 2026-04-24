package com.example.interfacehub.presentation;

import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/interfaces")
@Tag(name = "Interface Registry", description = "Interface registry and config management APIs")
public class InterfaceRegistryController {

    private final InterfaceRegistryService interfaceRegistryService;
    private final ExecutionHistoryRepository executionHistoryRepository;

    public InterfaceRegistryController(
        InterfaceRegistryService interfaceRegistryService,
        ExecutionHistoryRepository executionHistoryRepository
    ) {
        this.interfaceRegistryService = interfaceRegistryService;
        this.executionHistoryRepository = executionHistoryRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create interface", description = "Registers a new interface definition")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Interface created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Interface code already exists")
    })
    public InterfaceResponse createInterface(@Valid @RequestBody CreateInterfaceRequest request) {
        return InterfaceResponse.from(interfaceRegistryService.createInterface(request));
    }

    @GetMapping
    @Operation(summary = "List interfaces", description = "Returns all interfaces with configuration and execution summary")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interfaces returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<InterfaceResponse> findAllInterfaces() {
        return interfaceRegistryService.findAllInterfacesWithStats().stream()
            .map(stats -> InterfaceResponse.fromDetail(stats.definition(), stats.configCount(), stats.lastExecutedAt()))
            .toList();
    }

    @GetMapping("/{interfaceCode}")
    @Operation(summary = "Get interface detail", description = "Returns a single interface with config count and last execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interface returned"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public InterfaceResponse findInterface(@PathVariable String interfaceCode) {
        var definition = interfaceRegistryService.findByCode(interfaceCode);
        int configCount = interfaceRegistryService.countConfigs(definition);
        var lastExec = executionHistoryRepository
            .findFirstByInterfaceCodeOrderByStartedAtDesc(interfaceCode)
            .map(h -> h.getStartedAt())
            .orElse(null);
        return InterfaceResponse.fromDetail(definition, configCount, lastExec);
    }

    @PatchMapping("/{interfaceCode}/status")
    @Operation(summary = "Change interface status", description = "Updates interface lifecycle status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status changed"),
        @ApiResponse(responseCode = "400", description = "Invalid status change"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public InterfaceResponse changeStatus(
        @PathVariable String interfaceCode,
        @Valid @RequestBody ChangeInterfaceStatusRequest request
    ) {
        return InterfaceResponse.from(interfaceRegistryService.changeStatus(interfaceCode, request.status()));
    }

    @PostMapping("/{interfaceCode}/configs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create interface config", description = "Creates a new interface configuration version")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Config created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public ConfigResponse createConfig(
        @PathVariable String interfaceCode,
        @Valid @RequestBody CreateConfigRequest request
    ) {
        return ConfigResponse.from(interfaceRegistryService.createConfig(interfaceCode, request));
    }

    @GetMapping("/{interfaceCode}/configs")
    @Operation(summary = "List interface configs", description = "Returns paginated configuration versions for an interface")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configs returned"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public PagedResponse<ConfigResponse> findConfigs(
        @PathVariable String interfaceCode,
        @RequestParam(required = false) RuntimeEnvironment environment,
        @PageableDefault(size = 20, sort = "version", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            interfaceRegistryService.findConfigsByCode(interfaceCode, environment, pageable).map(ConfigResponse::from)
        );
    }

    @GetMapping("/{interfaceCode}/configs/compare")
    @Operation(summary = "Compare config versions", description = "Compares two configuration versions and returns changed fields")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comparison returned"),
        @ApiResponse(responseCode = "404", description = "Interface or config not found")
    })
    public ConfigComparisonResponse compareConfigs(
        @PathVariable String interfaceCode,
        @RequestParam Integer leftVersion,
        @RequestParam Integer rightVersion
    ) {
        return interfaceRegistryService.compareConfigVersions(interfaceCode, leftVersion, rightVersion);
    }

    @GetMapping("/{interfaceCode}/configs/{configId}")
    @Operation(summary = "Get config detail", description = "Returns one configuration version by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Config returned"),
        @ApiResponse(responseCode = "404", description = "Config not found")
    })
    public ConfigResponse findConfig(
        @PathVariable String interfaceCode,
        @PathVariable Long configId
    ) {
        return ConfigResponse.from(interfaceRegistryService.findConfigById(interfaceCode, configId));
    }

    @PostMapping("/{interfaceCode}/configs/{configId}/publish")
    @Operation(summary = "Publish config", description = "Publishes one configuration version as active")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Config published"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Config not found")
    })
    public ConfigResponse publishConfig(
        @PathVariable String interfaceCode,
        @PathVariable Long configId,
        @RequestParam(defaultValue = "system") String actor
    ) {
        return ConfigResponse.from(interfaceRegistryService.publishConfig(interfaceCode, configId, actor));
    }
}
