package com.example.interfacehub.domain.execution;

import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import org.springframework.util.MultiValueMap;

public record ExecutionContext(
    String interfaceCode,
    ProtocolType protocolType,
    String endpoint,
    MultiValueMap<String, String> headers,
    String payload,
    long timeoutMillis,
    String protocolConfigJson,
    Integer interfaceConfigVersion
) {
}
