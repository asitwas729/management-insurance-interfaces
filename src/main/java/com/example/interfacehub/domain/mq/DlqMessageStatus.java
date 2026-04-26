package com.example.interfacehub.domain.mq;

public enum DlqMessageStatus {
    PENDING,
    REPLAYED,
    EXHAUSTED
}

