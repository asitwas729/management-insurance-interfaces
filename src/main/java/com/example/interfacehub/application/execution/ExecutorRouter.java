package com.example.interfacehub.application.execution;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutorRouter {

    private final List<InterfaceExecutor> executors;

    public ExecutorRouter(List<InterfaceExecutor> executors) {
        this.executors = executors;
    }

    public ExecutionResult routeAndExecute(ExecutionContext context) {
        return executors.stream()
            .filter(executor -> executor.supportType() == context.protocolType())
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROTOCOL))
            .execute(context);
    }
}
