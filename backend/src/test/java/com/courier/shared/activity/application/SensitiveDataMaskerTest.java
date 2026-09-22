package com.courier.shared.activity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private SensitiveDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SensitiveDataMasker(new ObjectMapper());
    }

    @Test
    void masksKnownSensitiveFieldsInAMap() {
        Map<String, Object> masked = masker.mask(Map.of(
                "password", "secret123",
                "newPassword", "secret456",
                "accessToken", "abc.def.ghi",
                "otpCode", "1234",
                "status", "BOOKED"));

        assertThat(masked.get("password")).isEqualTo("***MASKED***");
        assertThat(masked.get("newPassword")).isEqualTo("***MASKED***");
        assertThat(masked.get("accessToken")).isEqualTo("***MASKED***");
        assertThat(masked.get("otpCode")).isEqualTo("***MASKED***");
        assertThat(masked.get("status")).isEqualTo("BOOKED");
    }

    @Test
    void masksNestedMaps() {
        Map<String, Object> masked = masker.mask(Map.of(
                "user", Map.of("password", "secret", "email", "a@b.com")));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) masked.get("user");
        assertThat(nested.get("password")).isEqualTo("***MASKED***");
        assertThat(nested.get("email")).isEqualTo("a@b.com");
    }

    @Test
    void masksJsonBody() {
        String json = "{\"cardNumber\":\"4111111111111111\",\"amount\":500}";
        String masked = masker.maskJson(json);

        assertThat(masked).contains("***MASKED***").doesNotContain("4111111111111111");
        assertThat(masked).contains("500");
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask(Map.of())).isEmpty();
        assertThat(masker.maskJson(null)).isNull();
    }

    @Test
    void malformedJsonIsDiscardedRatherThanLeaked() {
        assertThat(masker.maskJson("{not valid json")).isNull();
    }
}
