package com.example.interfacehub.presentation;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.example.interfacehub.application.mq.DlqReplayService;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
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
public class DlqController {

    private final DlqMessageService dlqMessageService;
    private final DlqReplayService dlqReplayService;

    public DlqController(DlqMessageService dlqMessageService, DlqReplayService dlqReplayService) {
        this.dlqMessageService = dlqMessageService;
        this.dlqReplayService = dlqReplayService;
    }

    @GetMapping
    public PagedResponse<DlqMessageResponse> findDlqMessages(String interfaceCode, Pageable pageable) {
        return PagedResponse.from(dlqMessageService.findAll(interfaceCode, pageable).map(DlqMessageResponse::from));
    }

    @PostMapping("/{dlqId}/replay-requests")
    public DlqReplayRequestResponse requestReplay(
        @PathVariable Long dlqId,
        @Valid @RequestBody CreateDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.requestReplay(dlqId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/approve")
    public DlqReplayRequestResponse approveReplayRequest(
        @PathVariable Long replayRequestId,
        @Valid @RequestBody ApproveDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.approve(replayRequestId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/reject")
    public DlqReplayRequestResponse rejectReplayRequest(
        @PathVariable Long replayRequestId,
        @Valid @RequestBody RejectDlqReplayRequest request
    ) {
        return DlqReplayRequestResponse.from(dlqReplayService.reject(replayRequestId, request));
    }

    @PostMapping("/replay-requests/{replayRequestId}/execute")
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
    public DlqReplayRequestResponse findReplayRequest(@PathVariable Long replayRequestId) {
        return DlqReplayRequestResponse.from(dlqReplayService.findById(replayRequestId));
    }

    @GetMapping("/replay-requests")
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
