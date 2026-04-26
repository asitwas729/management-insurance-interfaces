package com.example.interfacehub.application.fixedlength;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FixedLengthConfigResolver {

    private final ObjectMapper objectMapper;

    public FixedLengthConfigResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FixedLengthConfig resolve(String protocolConfigJson) {
        if (protocolConfigJson == null || protocolConfigJson.isBlank()) {
            return new FixedLengthConfig(false, false, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(protocolConfigJson);
            JsonNode fixed = root.path("fixedLength");
            if (fixed.isMissingNode() || !fixed.isObject()) {
                return new FixedLengthConfig(false, false, null, null);
            }

            boolean enforceRequest = fixed.path("enforceRequest").asBoolean(fixed.path("enforce").asBoolean(false));
            boolean enforceResponse = fixed.path("enforceResponse").asBoolean(false);
            String charset = fixed.path("charset").asText("UTF-8");

            FixedLengthSchema request = readSchema(charset, fixed.path("fields"));
            FixedLengthSchema response = readSchema(charset, fixed.path("responseFields"));

            return new FixedLengthConfig(enforceRequest, enforceResponse, request, response);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid protocolConfigJson.fixedLength: " + e.getMessage());
        }
    }

    private FixedLengthSchema readSchema(String charset, JsonNode fieldsNode) {
        if (fieldsNode == null || fieldsNode.isMissingNode() || !fieldsNode.isArray()) {
            return null;
        }
        List<FixedLengthFieldSpec> fields = new ArrayList<>();
        for (JsonNode f : fieldsNode) {
            String name = f.path("name").asText(null);
            int length = f.path("length").asInt(0);
            String padChar = f.path("padChar").asText(f.path("pad").asText(" "));
            String align = f.path("align").asText("LEFT");
            boolean required = f.path("required").asBoolean(false);
            boolean trim = f.path("trim").asBoolean(true);
            String defaultValue = f.path("defaultValue").asText(null);
            fields.add(new FixedLengthFieldSpec(name, length, padChar, align, required, trim, defaultValue));
        }
        return new FixedLengthSchema(charset, fields);
    }
}

