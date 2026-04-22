package com.example.interfacehub.application.mq;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.mq.DlqMessage;
import com.example.interfacehub.infrastructure.persistence.DlqMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DlqMessageService {

    private final DlqMessageRepository dlqMessageRepository;

    public DlqMessageService(DlqMessageRepository dlqMessageRepository) {
        this.dlqMessageRepository = dlqMessageRepository;
    }

    @Transactional
    public DlqMessage save(String interfaceCode, String topic, String payload, String reason) {
        DlqMessage message = DlqMessage.create(interfaceCode, topic, payload, reason);
        return dlqMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Page<DlqMessage> findAll(String interfaceCode, Pageable pageable) {
        if (interfaceCode == null || interfaceCode.isBlank()) {
            return dlqMessageRepository.findAll(pageable);
        }
        return dlqMessageRepository.findByInterfaceCodeOrderByCreatedAtDesc(interfaceCode, pageable);
    }

    @Transactional(readOnly = true)
    public DlqMessage findById(Long dlqId) {
        return dlqMessageRepository.findById(dlqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DLQ_NOT_FOUND));
    }

    @Transactional
    public void markReplayed(Long dlqId) {
        DlqMessage message = dlqMessageRepository.findById(dlqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DLQ_NOT_FOUND));
        message.markReplayed();
    }
}
