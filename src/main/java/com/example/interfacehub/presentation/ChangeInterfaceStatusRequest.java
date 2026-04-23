package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeInterfaceStatusRequest(@NotNull InterfaceStatus status) {}
