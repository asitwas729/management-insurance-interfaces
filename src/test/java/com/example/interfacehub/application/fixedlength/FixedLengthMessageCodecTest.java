package com.example.interfacehub.application.fixedlength;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixedLengthMessageCodecTest {

    private final FixedLengthMessageCodec codec = new FixedLengthMessageCodec();

    @Test
    void encodeBytes_pads_left_and_right_by_byte_length() {
        FixedLengthSchema schema = new FixedLengthSchema(
            "UTF-8",
            List.of(
                new FixedLengthFieldSpec("name", 5, " ", "LEFT", true, true, null),
                new FixedLengthFieldSpec("amount", 5, "0", "RIGHT", true, true, null)
            )
        );

        byte[] encoded = codec.encodeBytes(Map.of("name", "AB", "amount", "12"), schema);
        String asString = new String(encoded, Charset.forName("UTF-8"));

        assertThat(asString).isEqualTo("AB   00012");
        assertThat(encoded).hasSize(10);
    }

    @Test
    void encodeBytes_throws_when_required_missing() {
        FixedLengthSchema schema = new FixedLengthSchema(
            "UTF-8",
            List.of(new FixedLengthFieldSpec("id", 3, " ", "LEFT", true, true, null))
        );

        assertThatThrownBy(() -> codec.encodeBytes(Map.of(), schema))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.FIXED_LENGTH_VALIDATION_FAILED);
    }

    @Test
    void encodeBytes_throws_when_field_too_long_in_bytes() {
        FixedLengthSchema schema = new FixedLengthSchema(
            "UTF-8",
            List.of(new FixedLengthFieldSpec("id", 2, " ", "LEFT", true, true, null))
        );

        assertThatThrownBy(() -> codec.encodeBytes(Map.of("id", "ABC"), schema))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.FIXED_LENGTH_VALIDATION_FAILED);
    }

    @Test
    void decodeBytes_slices_by_bytes_and_trims_when_requested() {
        FixedLengthSchema schema = new FixedLengthSchema(
            "UTF-8",
            List.of(
                new FixedLengthFieldSpec("a", 3, " ", "LEFT", true, true, null),
                new FixedLengthFieldSpec("b", 2, " ", "LEFT", true, false, null)
            )
        );

        byte[] messageBytes = "X  YZ".getBytes(Charset.forName("UTF-8"));
        Map<String, String> decoded = codec.decodeBytes(messageBytes, schema);

        assertThat(decoded.get("a")).isEqualTo("X");
        assertThat(decoded.get("b")).isEqualTo("YZ");
    }

    @Test
    void encodeBytes_supports_ms949_byte_lengths_for_korean_characters() {
        FixedLengthSchema schema = new FixedLengthSchema(
            "MS949",
            List.of(new FixedLengthFieldSpec("k", 4, " ", "LEFT", true, true, null))
        );

        byte[] encoded = codec.encodeBytes(Map.of("k", "가"), schema);
        assertThat(encoded).hasSize(4);

        Map<String, String> decoded = codec.decodeBytes(encoded, schema);
        assertThat(decoded.get("k")).isEqualTo("가");
    }
}

