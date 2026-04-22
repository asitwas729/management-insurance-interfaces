package com.example.interfacehub.domain.mq;

public enum DlqReplayStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXECUTED,
    FAILED
}
