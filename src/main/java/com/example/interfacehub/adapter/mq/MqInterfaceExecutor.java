package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import org.springframework.stereotype.Component;

@Component
public class MqInterfaceExecutor implements InterfaceExecutor {

    private final MqGateway mqGateway;
    private final ExternalCallResilienceService resilienceService;

    public MqInterfaceExecutor(
        MqGateway mqGateway,
        ExternalCallResilienceService resilienceService
    ) {
        this.mqGateway = mqGateway;
        this.resilienceService = resilienceService;
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.MQ;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return resilienceService.execute(context.interfaceCode(), () -> invoke(context), ErrorCode.MQ_CONSUME_FAILED);
    }

    private ExecutionResult invoke(ExecutionContext context) {
        long start = System.currentTimeMillis();
        MqProcessResult result = mqGateway.publish(
            context.interfaceCode(),
            context.endpoint(),
            context.payload()
        );

        long latency = System.currentTimeMillis() - start;
        if (result.success()) {
            return ExecutionResult.success(result.responsePayload(), latency);
        }
        return ExecutionResult.failure(ErrorCode.MQ_CONSUME_FAILED.name(), result.errorMessage(), latency);
    }
}
