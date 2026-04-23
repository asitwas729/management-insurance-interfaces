package com.example.interfacehub.presentation;

import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
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
    public InterfaceResponse createInterface(@Valid @RequestBody CreateInterfaceRequest request) {
        return InterfaceResponse.from(interfaceRegistryService.createInterface(request));
    }

    @GetMapping
    public List<InterfaceResponse> findAllInterfaces() {
        return interfaceRegistryService.findAllInterfacesWithStats().stream()
            .map(stats -> InterfaceResponse.fromDetail(stats.definition(), stats.configCount(), stats.lastExecutedAt()))
            .toList();
    }

    @GetMapping("/{interfaceCode}")
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
    public InterfaceResponse changeStatus(
        @PathVariable String interfaceCode,
        @Valid @RequestBody ChangeInterfaceStatusRequest request
    ) {
        return InterfaceResponse.from(interfaceRegistryService.changeStatus(interfaceCode, request.status()));
    }

    @PostMapping("/{interfaceCode}/configs")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigResponse createConfig(
        @PathVariable String interfaceCode,
        @Valid @RequestBody CreateConfigRequest request
    ) {
        return ConfigResponse.from(interfaceRegistryService.createConfig(interfaceCode, request));
    }

    @GetMapping("/{interfaceCode}/configs")
    public PagedResponse<ConfigResponse> findConfigs(
        @PathVariable String interfaceCode,
        @PageableDefault(size = 20, sort = "version", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            interfaceRegistryService.findConfigsByCode(interfaceCode, pageable).map(ConfigResponse::from)
        );
    }

    @GetMapping("/{interfaceCode}/configs/{configId}")
    public ConfigResponse findConfig(
        @PathVariable String interfaceCode,
        @PathVariable Long configId
    ) {
        return ConfigResponse.from(interfaceRegistryService.findConfigById(interfaceCode, configId));
    }

    @PostMapping("/{interfaceCode}/configs/{configId}/publish")
    public ConfigResponse publishConfig(
        @PathVariable String interfaceCode,
        @PathVariable Long configId,
        @RequestParam(defaultValue = "system") String actor
    ) {
        return ConfigResponse.from(interfaceRegistryService.publishConfig(interfaceCode, configId, actor));
    }
}
