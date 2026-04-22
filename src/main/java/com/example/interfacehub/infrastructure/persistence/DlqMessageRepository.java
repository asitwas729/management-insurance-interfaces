package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.mq.DlqMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

    Page<DlqMessage> findByInterfaceCodeOrderByCreatedAtDesc(String interfaceCode, Pageable pageable);
}
