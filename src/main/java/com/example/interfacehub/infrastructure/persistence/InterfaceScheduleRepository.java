package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.scheduler.InterfaceSchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterfaceScheduleRepository extends JpaRepository<InterfaceSchedule, Long> {

    Optional<InterfaceSchedule> findByInterfaceCode(String interfaceCode);

    List<InterfaceSchedule> findByEnabledTrue();
}
