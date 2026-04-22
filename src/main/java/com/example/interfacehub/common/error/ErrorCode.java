package com.example.interfacehub.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    IF_NOT_FOUND(HttpStatus.NOT_FOUND, "Interface not found"),
    CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Published interface config not found"),
    CONFIG_NOT_BELONG_TO_INTERFACE(HttpStatus.BAD_REQUEST, "Config does not belong to interface"),
    DUPLICATE_INTERFACE_CODE(HttpStatus.CONFLICT, "Interface code already exists"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Duplicate request"),
    EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Execution not found"),
    RETRY_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Retry task not found"),
    RETRY_NOT_APPROVED(HttpStatus.BAD_REQUEST, "Retry task is not approved"),
    RETRY_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Retry task has invalid status"),
    INVALID_CONFIG(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid interface configuration"),
    UNSUPPORTED_PROTOCOL(HttpStatus.BAD_REQUEST, "Unsupported protocol"),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "Protocol adapter not yet implemented"),
    REST_CALL_FAILED(HttpStatus.BAD_GATEWAY, "REST call failed"),
    SOAP_CALL_FAILED(HttpStatus.BAD_GATEWAY, "SOAP call failed"),
    BATCH_FAILED(HttpStatus.BAD_GATEWAY, "Batch execution failed"),
    SFTP_TRANSFER_FAILED(HttpStatus.BAD_GATEWAY, "SFTP transfer failed"),
    EXT_4XX(HttpStatus.BAD_GATEWAY, "External agency returned 4xx"),
    EXT_5XX(HttpStatus.BAD_GATEWAY, "External agency returned 5xx"),
    EXT_MAINTENANCE(HttpStatus.SERVICE_UNAVAILABLE, "External agency is in maintenance window"),
    MQ_CONSUME_FAILED(HttpStatus.BAD_GATEWAY, "MQ consume failed"),
    DLQ_NOT_FOUND(HttpStatus.NOT_FOUND, "DLQ message not found"),
    DLQ_REPLAY_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "DLQ replay request not found"),
    DLQ_REPLAY_NOT_APPROVED(HttpStatus.BAD_REQUEST, "DLQ replay request is not approved"),
    DLQ_REPLAY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "DLQ replay attempt limit exceeded"),
    DLQ_REPLAY_COOLDOWN(HttpStatus.TOO_EARLY, "DLQ replay is in cooldown period"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Actor role is not permitted"),
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "Circuit breaker is open"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    BULKHEAD_FULL(HttpStatus.SERVICE_UNAVAILABLE, "Bulkhead is full"),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "External call timed out"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
