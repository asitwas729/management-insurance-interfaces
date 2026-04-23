package com.example.interfacehub.presentation;

import com.example.interfacehub.application.scheduler.InterfaceScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/schedules")
public class InterfaceScheduleController {

    private final InterfaceScheduleService scheduleService;

    public InterfaceScheduleController(InterfaceScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<InterfaceScheduleResponse> findAll() {
        return scheduleService.findAll().stream().map(InterfaceScheduleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceScheduleResponse create(@Valid @RequestBody CreateInterfaceScheduleRequest request) {
        return InterfaceScheduleResponse.from(scheduleService.create(request));
    }

    @PatchMapping("/{interfaceCode}/enabled")
    public InterfaceScheduleResponse updateEnabled(
        @PathVariable String interfaceCode,
        @RequestParam boolean enabled
    ) {
        return InterfaceScheduleResponse.from(scheduleService.updateEnabled(interfaceCode, enabled));
    }

    @DeleteMapping("/{interfaceCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String interfaceCode) {
        scheduleService.delete(interfaceCode);
    }
}
