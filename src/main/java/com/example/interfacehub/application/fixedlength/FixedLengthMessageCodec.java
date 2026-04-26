package com.example.interfacehub.application.fixedlength;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FixedLengthMessageCodec {

    public byte[] encodeBytes(Map<String, Object> values, FixedLengthSchema schema) {
        validateSchema(schema);
        Map<String, Object> safe = values == null ? Map.of() : values;

        Charset charset = resolveCharset(schema);
        byte[] out = new byte[schema.totalLength()];
        int offset = 0;
        for (FixedLengthFieldSpec field : schema.fields()) {
            String raw = resolveValue(safe, field);
            byte[] fieldBytes = padAndValidateToBytes(raw, field, charset);
            System.arraycopy(fieldBytes, 0, out, offset, fieldBytes.length);
            offset += fieldBytes.length;
        }
        return out;
    }

    public String encode(Map<String, Object> values, FixedLengthSchema schema) {
        Charset charset = resolveCharset(schema);
        return new String(encodeBytes(values, schema), charset);
    }

    public Map<String, String> decodeBytes(byte[] messageBytes, FixedLengthSchema schema) {
        validateSchema(schema);
        if (messageBytes == null) {
            throw new BusinessException(ErrorCode.FIXED_LENGTH_VALIDATION_FAILED, "fixed-length message is null");
        }

        int expected = schema.totalLength();
        if (messageBytes.length != expected) {
            throw new BusinessException(
                ErrorCode.FIXED_LENGTH_VALIDATION_FAILED,
                "fixed-length length mismatch: expected=" + expected + ", actual=" + messageBytes.length
            );
        }

        Charset charset = resolveCharset(schema);
        Map<String, String> result = new LinkedHashMap<>();
        int offset = 0;
        for (FixedLengthFieldSpec field : schema.fields()) {
            int len = field.length();
            byte[] slice = Arrays.copyOfRange(messageBytes, offset, offset + len);
            offset += len;
            String decoded = new String(slice, charset);
            result.put(field.name(), field.trim() ? decoded.trim() : decoded);
        }
        return result;
    }

    public Map<String, String> decode(String message, FixedLengthSchema schema) {
        validateSchema(schema);
        if (message == null) {
            throw new BusinessException(ErrorCode.FIXED_LENGTH_VALIDATION_FAILED, "fixed-length message is null");
        }
        Charset charset = resolveCharset(schema);
        return decodeBytes(message.getBytes(charset), schema);
    }

    public String decodeBytesToString(byte[] messageBytes, FixedLengthSchema schema) {
        Charset charset = resolveCharset(schema);
        return new String(messageBytes, charset);
    }

    public int byteLength(String value, FixedLengthSchema schema) {
        Charset charset = resolveCharset(schema);
        return (value == null ? "" : value).getBytes(charset).length;
    }

    public int byteLength(String value, Charset charset) {
        return (value == null ? "" : value).getBytes(charset).length;
    }

    public int totalByteLength(FixedLengthSchema schema) {
        validateSchema(schema);
        int expected = schema.totalLength();
        return expected;
    }

    public Charset resolveCharset(FixedLengthSchema schema) {
        String name = schema == null ? null : schema.charset();
        if (name == null || name.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid charset: " + name);
        }
    }

    private void validateSchema(FixedLengthSchema schema) {
        if (schema == null || schema.fields() == null || schema.fields().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "fixedLength schema.fields is required");
        }
        for (FixedLengthFieldSpec field : schema.fields()) {
            if (field == null || field.name() == null || field.name().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "fixedLength field.name is required");
            }
            if (field.length() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "fixedLength field.length must be > 0");
            }
        }

        Charset charset = resolveCharset(schema);
        for (FixedLengthFieldSpec field : schema.fields()) {
            String pad = field.resolvedPadChar();
            if (pad.length() != 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "fixedLength padChar must be a single character");
            }
            byte[] padBytes = pad.getBytes(charset);
            if (padBytes.length != 1) {
                throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "fixedLength padChar must be 1 byte in charset=" + charset.name()
                );
            }
        }
    }

    private String resolveValue(Map<String, Object> values, FixedLengthFieldSpec field) {
        Object value = values.get(field.name());
        String raw = value == null ? null : String.valueOf(value);
        if (raw == null || raw.isBlank()) {
            raw = field.defaultValue();
        }
        if ((raw == null || raw.isBlank()) && field.required()) {
            throw new BusinessException(ErrorCode.FIXED_LENGTH_VALIDATION_FAILED, "required field missing: " + field.name());
        }
        return raw == null ? "" : raw;
    }

    private byte[] padAndValidateToBytes(String raw, FixedLengthFieldSpec field, Charset charset) {
        String value = raw == null ? "" : raw;
        int len = field.length();
        byte[] valueBytes = value.getBytes(charset);
        if (valueBytes.length > len) {
            throw new BusinessException(
                ErrorCode.FIXED_LENGTH_VALIDATION_FAILED,
                "field too long: " + field.name() + " (bytes=" + valueBytes.length + ", maxBytes=" + len + ")"
            );
        }

        byte padByte = field.resolvedPadChar().getBytes(charset)[0];
        int padCount = len - valueBytes.length;
        byte[] out = new byte[len];
        if (padCount == 0) {
            System.arraycopy(valueBytes, 0, out, 0, valueBytes.length);
            return out;
        }

        if (field.rightAlign()) {
            Arrays.fill(out, 0, padCount, padByte);
            System.arraycopy(valueBytes, 0, out, padCount, valueBytes.length);
            return out;
        }

        System.arraycopy(valueBytes, 0, out, 0, valueBytes.length);
        Arrays.fill(out, valueBytes.length, len, padByte);
        return out;
    }
}
