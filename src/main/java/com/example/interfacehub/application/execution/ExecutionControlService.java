package com.example.interfacehub.application.execution;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ExecutionControlService {

    private final Set<String> stopRequestedExecutionIds = ConcurrentHashMap.newKeySet();

    public void requestStop(String executionId) {
        stopRequestedExecutionIds.add(executionId);
    }

    public boolean isStopRequested(String executionId) {
        return stopRequestedExecutionIds.contains(executionId);
    }

    public void clearStopRequest(String executionId) {
        stopRequestedExecutionIds.remove(executionId);
    }
}
