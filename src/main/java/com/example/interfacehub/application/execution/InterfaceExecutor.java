package com.example.interfacehub.application.execution;

import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;

public interface InterfaceExecutor {

    ProtocolType supportType();

    ExecutionResult execute(ExecutionContext context);
}
