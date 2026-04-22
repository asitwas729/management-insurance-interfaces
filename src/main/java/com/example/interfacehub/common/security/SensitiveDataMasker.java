package com.example.interfacehub.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "residentNo", "rrn", "juminNo",
        "phone", "phoneNumber", "mobile", "tel",
        "cardNo", "cardNumber",
        "accountNo", "accountNumber",
        "email", "emailAddress"
    );

    private final ObjectMapper objectMapper;

    public SensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }

        String masked = maskStructuredPayload(raw);
        masked = maskByPattern(masked);
        return masked;
    }

    private String maskStructuredPayload(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode masked = deepMask(root);
            return objectMapper.writeValueAsString(masked);
        } catch (Exception ignore) {
            return raw;
        }
    }

    private JsonNode deepMask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node.deepCopy();
            objectNode.fieldNames().forEachRemaining(field -> {
                JsonNode child = objectNode.get(field);
                if (child == null) {
                    return;
                }

                if (SENSITIVE_KEYS.contains(field)) {
                    objectNode.put(field, maskValue(child.asText()));
                    return;
                }

                objectNode.set(field, deepMask(child));
            });
            return objectNode;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(child -> arrayNode.add(deepMask(child)));
            return arrayNode;
        }

        return node;
    }

    private String maskByPattern(String raw) {
        String masked = raw;
        masked = masked.replaceAll("(?<!\\d)\\d{6}-\\d{7}(?!\\d)", "******-*******");
        masked = masked.replaceAll("(?<!\\d)(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})(?!\\d)", "$1-****-****-$4");
        masked = masked.replaceAll("(?<!\\d)(01[0-9])[- ]?(\\d{3,4})[- ]?(\\d{4})(?!\\d)", "$1-****-$3");
        masked = masked.replaceAll("([A-Za-z0-9._%+-]{2})[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+)", "$1***@$2");
        return masked;
    }

    private String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
