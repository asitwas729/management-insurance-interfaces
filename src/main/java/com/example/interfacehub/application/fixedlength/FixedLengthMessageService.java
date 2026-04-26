package com.example.interfacehub.application.fixedlength;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FixedLengthMessageService {

    private final ObjectMapper objectMapper;
    private final FixedLengthConfigResolver resolver;
    private final FixedLengthMessageCodec codec;

    public FixedLengthMessageService(ObjectMapper objectMapper, FixedLengthConfigResolver resolver) {
        this.objectMapper = objectMapper;
        this.resolver = resolver;
        this.codec = new FixedLengthMessageCodec();
    }

    public FixedLengthConfig resolve(String protocolConfigJson) {
        return resolver.resolve(protocolConfigJson);
    }

    public byte[] encodeJsonPayloadToFixedBytes(String jsonPayload, FixedLengthSchema schema) {
        try {
            Map<String, Object> map = objectMapper.readValue(jsonPayload == null ? "{}" : jsonPayload, new TypeReference<>() {
            });
            return codec.encodeBytes(map, schema);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JSON payload for fixed-length encoding");
        }
    }

    public String encodeJsonPayloadToFixedString(String jsonPayload, FixedLengthSchema schema) {
        Charset charset = codec.resolveCharset(schema);
        return new String(encodeJsonPayloadToFixedBytes(jsonPayload, schema), charset);
    }

    public String decodeFixedBytesToJson(byte[] fixedMessageBytes, FixedLengthSchema schema) {
        Map<String, String> decoded = codec.decodeBytes(fixedMessageBytes, schema);
        try {
            return objectMapper.writeValueAsString(decoded);
        } catch (Exception e) {
            return "{}";
        }
    }

    public String decodeFixedStringToJson(String fixedMessage, FixedLengthSchema schema) {
        Map<String, String> decoded = codec.decode(fixedMessage, schema);
        try {
            return objectMapper.writeValueAsString(decoded);
        } catch (Exception e) {
            return "{}";
        }
    }
}
