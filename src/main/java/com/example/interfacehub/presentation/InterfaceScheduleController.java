package com.example.interfacehub.presentation;

import com.example.interfacehub.application.scheduler.InterfaceScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Interface Schedules", description = "Interface scheduler management APIs")
public class InterfaceScheduleController {

    private final InterfaceScheduleService scheduleService;

    public InterfaceScheduleController(InterfaceScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @Operation(summary = "List schedules", description = "Returns all interface schedules")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schedules returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<InterfaceScheduleResponse> findAll() {
        return scheduleService.findAll().stream().map(InterfaceScheduleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create schedule", description = "Creates a schedule for an interface")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schedule created"),
        @ApiResponse(responseCode = "400", description = "Invalid schedule request")
    })
    public InterfaceScheduleResponse create(@Valid @RequestBody CreateInterfaceScheduleRequest request) {
        return InterfaceScheduleResponse.from(scheduleService.create(request));
    }

    @PatchMapping("/{interfaceCode}/enabled")
    @Operation(summary = "Update schedule enabled flag", description = "Enables or disables a schedule by interface code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schedule updated"),
        @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    public InterfaceScheduleResponse updateEnabled(
        @PathVariable String interfaceCode,
        @RequestParam boolean enabled
    ) {
        return InterfaceScheduleResponse.from(scheduleService.updateEnabled(interfaceCode, enabled));
    }

    @DeleteMapping("/{interfaceCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete schedule", description = "Deletes schedule by interface code")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Schedule deleted"),
        @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    public void delete(@PathVariable String interfaceCode) {
        scheduleService.delete(interfaceCode);
    }
}
