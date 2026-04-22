package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.standard.MaintenanceWindow;

public record MaintenanceWindowResponse(
    Long id,
    String externalOrg,
    String dayOfWeek,
    String startTime,
    String endTime,
    String suppressLevel,
    boolean enabled,
    String reason
) {
    public static MaintenanceWindowResponse from(MaintenanceWindow window) {
        return new MaintenanceWindowResponse(
            window.getId(),
            window.getExternalOrg(),
            window.getDayOfWeek(),
            window.getStartTime().toString(),
            window.getEndTime().toString(),
            window.getSuppressLevel(),
            window.isEnabled(),
            window.getReason()
        );
    }
}
