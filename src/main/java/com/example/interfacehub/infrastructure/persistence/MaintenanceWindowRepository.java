package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.standard.MaintenanceWindow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {

    List<MaintenanceWindow> findByExternalOrgOrderByDayOfWeekAscStartTimeAsc(String externalOrg);
}
