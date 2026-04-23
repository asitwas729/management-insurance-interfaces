package com.example.interfacehub.presentation;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.example.interfacehub.application.mq.DlqReplayService;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlq")
@Tag(name = "DLQ", description = "Dead-letter queue and replay workflow APIs")
public class DlqController {

    private final DlqMessageService dlqMessageService;
    private final DlqReplayService dlqReplayService;

    public DlqController(DlqMessageService dlqMessageService, DlqReplayService dlqReplayService) {
        this.dlqMessageService = dlqMessageService;
        this.dlqReplayService = dlqReplayService;
    }

    @GetMapping
    @Operation(summary = "List DLQ messages", description = "Returns DLQ messages filtered by interface code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "DLQ messages returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PagedResponse<DlqMessageResponse> findDlqMessages(
        @RequestParam(name = "interfaceCode", required = false) String interfaceCode,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(dlqMessageService.findAll(interfaceCode, pageable).map(DlqMessageResponse::from));
    }

    @GetMapping("/{dlqId}")
    @Operation(summary = "Get DLQ message", description = "Returns a DLQ message by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "DLQ message returned"),
        @ApiResponse(responseCode = "404", description = "DLQ message not found")
    })
    public DlqMessageResponse findDlqMessage(@PathVariable Long dlqId) {
        return DlqMessageResponse.from(dlqMessageService.findById(dlqId));
    }

    @PostMapping("/{dlqId}/replay-requests")
    @Operation(summary = "Create replay request", description = "Creates a replay request for a DLQ message")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay request created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "DLQ message not found")
    })
    public DlqReplayRequestResponse requestReplay(
        @PathVariable Long dlqId,
        @Valid @RequestBody CreateDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.requestReplay(dlqId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/approve")
    @Operation(summary = "Approve replay request", description = "Approves a pending DLQ replay request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay request approved"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Replay request not found")
    })
    public DlqReplayRequestResponse approveReplayRequest(
        @PathVariable Long replayRequestId,
        @Valid @RequestBody ApproveDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.approve(replayRequestId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/reject")
    @Operation(summary = "Reject replay request", description = "Rejects a pending DLQ replay request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay request rejected"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Replay request not found")
    })
    public DlqReplayRequestResponse rejectReplayRequest(
        @PathVariable Long replayRequestId,
        @Valid @RequestBody RejectDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.reject(replayRequestId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/execute")
    @Operation(summary = "Execute replay request", description = "Executes an approved replay request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay executed"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Replay request not found")
    })
    public DlqReplayResponse executeReplayRequest(
        @PathVariable Long replayRequestId,
        @Valid @RequestBody ExecuteDlqReplayRequest request
    ) {
        return DlqReplayResponse.from(
            dlqReplayService.findById(replayRequestId).getDlqMessage().getId(),
            dlqReplayService.execute(replayRequestId, request)
        );
    }

    @GetMapping("/replay-requests/{replayRequestId}")
    @Operation(summary = "Get replay request", description = "Returns DLQ replay request detail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay request returned"),
        @ApiResponse(responseCode = "404", description = "Replay request not found")
    })
    public DlqReplayRequestResponse findReplayRequest(@PathVariable Long replayRequestId) {
        return DlqReplayRequestResponse.from(dlqReplayService.findById(replayRequestId));
    }

    @GetMapping("/replay-requests")
    @Operation(summary = "List replay requests", description = "Returns DLQ replay requests filtered by status/date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replay requests returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PagedResponse<DlqReplayRequestResponse> findReplayRequests(
        @RequestParam(required = false) DlqReplayStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            dlqReplayService.search(status, fromDate, toDate, pageable).map(DlqReplayRequestResponse::from)
        );
    }
}
