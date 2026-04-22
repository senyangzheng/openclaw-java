package com.openclaw.common.json;

import com.openclaw.common.error.OpenClawException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonCodecTest {

    public record Sample(String name, int age, List<String> tags) {
    }

    @Test
    void shouldRoundTripRecord() {
        final Sample original = new Sample("claw", 3, List.of("a", "b"));

        final String json = JsonCodec.toJson(original);
        final Sample restored = JsonCodec.fromJson(json, Sample.class);

        assertThat(json).contains("\"name\":\"claw\"");
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void shouldDeserializeToMap() {
        final Map<String, Object> map = JsonCodec.fromJsonMap("{\"a\":1,\"b\":\"x\"}");

        assertThat(map).containsEntry("a", 1).containsEntry("b", "x");
    }

    @Test
    void shouldWrapDeserializationFailure() {
        assertThatThrownBy(() -> JsonCodec.fromJson("{not json", Sample.class))
            .isInstanceOf(OpenClawException.class)
            .hasMessageContaining("Failed to deserialize");
    }
}
