package com.example.interfacehub.presentation;

import com.example.interfacehub.application.registry.InterfaceRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

    public InterfaceRegistryController(InterfaceRegistryService interfaceRegistryService) {
        this.interfaceRegistryService = interfaceRegistryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceResponse createInterface(@Valid @RequestBody CreateInterfaceRequest request) {
        return InterfaceResponse.from(interfaceRegistryService.createInterface(request));
    }

    @GetMapping
    public List<InterfaceResponse> findAllInterfaces() {
        return interfaceRegistryService.findAllInterfaces().stream()
            .map(InterfaceResponse::from)
            .toList();
    }

    @GetMapping("/{interfaceCode}")
    public InterfaceResponse findInterface(@PathVariable String interfaceCode) {
        return InterfaceResponse.from(interfaceRegistryService.findByCode(interfaceCode));
    }

    @PostMapping("/{interfaceCode}/configs")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigResponse createConfig(
        @PathVariable String interfaceCode,
        @Valid @RequestBody CreateConfigRequest request
    ) {
        return ConfigResponse.from(interfaceRegistryService.createConfig(interfaceCode, request));
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
