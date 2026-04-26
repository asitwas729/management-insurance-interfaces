package com.example.interfacehub.presentation;

import com.example.interfacehub.application.logging.ApiRequestLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/debug-logs")
@Tag(name = "Debug Logs", description = "API request logs collected from UI")
public class ApiRequestLogController {

    private final ApiRequestLogService apiRequestLogService;

    public ApiRequestLogController(ApiRequestLogService apiRequestLogService) {
        this.apiRequestLogService = apiRequestLogService;
    }

    @PostMapping
    @Operation(summary = "Save API request log", description = "Stores UI API request logs and forwards to central logging")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Saved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ApiRequestLogResponse create(
        Authentication authentication,
        @RequestBody CreateApiRequestLogRequest request
    ) {
        String actor = authentication == null ? null : authentication.getName();
        return ApiRequestLogResponse.from(apiRequestLogService.saveUiLog(actor, request));
    }

    @GetMapping
    @Operation(summary = "List API request logs", description = "Returns paginated UI API logs (latest first)")
    public PagedResponse<ApiRequestLogResponse> list(
        @RequestParam(required = false) String q,
        @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(apiRequestLogService.search(q, pageable).map(ApiRequestLogResponse::from));
    }
}

