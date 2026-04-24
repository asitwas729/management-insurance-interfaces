package com.example.interfacehub.presentation;

import java.util.List;

public record GlobalSearchResponse(List<SearchItem> items) {

    public record SearchItem(
        String type,
        String title,
        String subtitle,
        String interfaceCode,
        String executionId,
        Long configId,
        Long retryTaskId
    ) {
        public static SearchItem interfaceItem(String interfaceCode, String name, String protocol, String status) {
            String subtitle = "";
            if (!protocol.isBlank()) {
                subtitle = protocol;
            }
            if (!status.isBlank()) {
                subtitle = subtitle.isBlank() ? status : subtitle + " / " + status;
            }
            return new SearchItem("INTERFACE", name, subtitle, interfaceCode, null, null, null);
        }

        public static SearchItem configItem(Long configId, String interfaceCode, Integer version, String env, String endpoint) {
            String v = version == null ? "-" : String.valueOf(version);
            String subtitle = (env == null || env.isBlank()) ? ("v" + v) : (env + " / v" + v);
            String title = endpoint == null ? "Config" : endpoint;
            return new SearchItem("CONFIG", title, subtitle, interfaceCode, null, configId, null);
        }

        public static SearchItem historyItem(
            String interfaceCode,
            String executionId,
            String status,
            String errorCode,
            String errorMessage
        ) {
            String title = executionId == null ? "-" : executionId;
            String subtitle = status == null ? "" : status;
            if (errorCode != null && !errorCode.isBlank()) {
                subtitle = subtitle.isBlank() ? errorCode : subtitle + " / " + errorCode;
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                String trimmed = errorMessage.length() > 80 ? errorMessage.substring(0, 80) + "..." : errorMessage;
                subtitle = subtitle.isBlank() ? trimmed : subtitle + " / " + trimmed;
            }
            return new SearchItem("HISTORY", title, subtitle, interfaceCode, executionId, null, null);
        }

        public static SearchItem retryItem(
            Long retryTaskId,
            String interfaceCode,
            String originalExecutionId,
            String status,
            String requester
        ) {
            String title = originalExecutionId == null ? "-" : originalExecutionId;
            String subtitle = status == null ? "" : status;
            if (requester != null && !requester.isBlank()) {
                subtitle = subtitle.isBlank() ? requester : subtitle + " / " + requester;
            }
            return new SearchItem("RETRY", title, subtitle, interfaceCode, originalExecutionId, null, retryTaskId);
        }
    }
}

