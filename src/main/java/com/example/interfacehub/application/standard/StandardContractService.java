package com.example.interfacehub.application.standard;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.standard.ErrorCatalog;
import com.example.interfacehub.domain.standard.MaintenanceWindow;
import com.example.interfacehub.domain.standard.ReprocessPolicy;
import com.example.interfacehub.infrastructure.persistence.ErrorCatalogRepository;
import com.example.interfacehub.infrastructure.persistence.MaintenanceWindowRepository;
import com.example.interfacehub.infrastructure.persistence.ReprocessPolicyRepository;
import com.example.interfacehub.presentation.CreateMaintenanceWindowRequest;
import com.example.interfacehub.presentation.UpsertReprocessPolicyRequest;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandardContractService {

    private final ErrorCatalogRepository errorCatalogRepository;
    private final ReprocessPolicyRepository reprocessPolicyRepository;
    private final MaintenanceWindowRepository maintenanceWindowRepository;

    public StandardContractService(
        ErrorCatalogRepository errorCatalogRepository,
        ReprocessPolicyRepository reprocessPolicyRepository,
        MaintenanceWindowRepository maintenanceWindowRepository
    ) {
        this.errorCatalogRepository = errorCatalogRepository;
        this.reprocessPolicyRepository = reprocessPolicyRepository;
        this.maintenanceWindowRepository = maintenanceWindowRepository;
    }

    @Transactional(readOnly = true)
    public List<ErrorCatalog> findErrorCatalogs() {
        return errorCatalogRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ReprocessPolicy> findReprocessPolicies() {
        return reprocessPolicyRepository.findAll();
    }

    @Transactional
    public ReprocessPolicy upsertReprocessPolicy(String errorCode, UpsertReprocessPolicyRequest request) {
        if (!errorCatalogRepository.existsById(errorCode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unknown error code: " + errorCode);
        }
        ReprocessPolicy policy = reprocessPolicyRepository.findByErrorCode(errorCode)
            .orElseGet(() -> ReprocessPolicy.createDefault(errorCode));
        policy.update(
            request.mode(),
            request.autoMaxAttempts(),
            request.backoffSeconds(),
            request.approvalLevel(),
            request.enabled()
        );
        return reprocessPolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindow> findMaintenanceWindows(String externalOrg) {
        if (externalOrg == null || externalOrg.isBlank()) {
            return maintenanceWindowRepository.findAll();
        }
        return maintenanceWindowRepository.findByExternalOrgOrderByDayOfWeekAscStartTimeAsc(externalOrg.trim().toUpperCase());
    }

    @Transactional
    public MaintenanceWindow createMaintenanceWindow(CreateMaintenanceWindowRequest request) {
        MaintenanceWindow window = MaintenanceWindow.create(
            request.externalOrg().trim().toUpperCase(),
            request.dayOfWeek(),
            request.startTime(),
            request.endTime(),
            request.suppressLevel(),
            request.reason()
        );
        return maintenanceWindowRepository.save(window);
    }

    @Transactional(readOnly = true)
    public boolean isMaintenanceWindowActive(String externalOrg, LocalDateTime at) {
        if (externalOrg == null || externalOrg.isBlank()) {
            return false;
        }
        String orgKey = externalOrg.trim().toUpperCase();
        DayOfWeek day = at.getDayOfWeek();
        LocalTime now = at.toLocalTime();
        return maintenanceWindowRepository.findByExternalOrgOrderByDayOfWeekAscStartTimeAsc(orgKey).stream()
            .filter(MaintenanceWindow::isEnabled)
            .filter(window -> window.getDayOfWeek().equals(day.name()))
            .anyMatch(window -> isWithin(now, window.getStartTime(), window.getEndTime()));
    }

    private boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
