package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.standard.ReprocessPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReprocessPolicyRepository extends JpaRepository<ReprocessPolicy, Long> {

    Optional<ReprocessPolicy> findByErrorCode(String errorCode);
}
