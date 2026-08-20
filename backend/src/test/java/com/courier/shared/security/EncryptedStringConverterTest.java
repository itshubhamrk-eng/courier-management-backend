package com.courier.shared.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static EncryptedStringConverter converterWithKey(String key) {
        SecretsEncryptionProperties properties = new SecretsEncryptionProperties();
        properties.setEncryptionKey(key);
        return new EncryptedStringConverter(properties);
    }

    @Test
    void roundTripsAValue() {
        EncryptedStringConverter converter = converterWithKey(randomKey());

        String stored = converter.convertToDatabaseColumn("rzp_test_secret_abc123");

        assertThat(stored).isNotEqualTo("rzp_test_secret_abc123");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("rzp_test_secret_abc123");
    }

    @Test
    void nullAndBlankPassThroughAsNull() {
        EncryptedStringConverter converter = converterWithKey(randomKey());

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        // A fresh random IV per call — two ciphertexts of the same plaintext must not match,
        // or an attacker could tell two rows hold the same secret without decrypting either.
        EncryptedStringConverter converter = converterWithKey(randomKey());

        String first = converter.convertToDatabaseColumn("same-secret");
        String second = converter.convertToDatabaseColumn("same-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo("same-secret");
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo("same-secret");
    }

    @Test
    void refusesToEncryptWithoutAKey() {
        EncryptedStringConverter converter = converterWithKey(null);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("a-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key");
    }

    @Test
    void refusesToDecryptWithoutAKey() {
        String stored = converterWithKey(randomKey()).convertToDatabaseColumn("a-secret");
        EncryptedStringConverter withoutKey = converterWithKey(null);

        assertThatThrownBy(() -> withoutKey.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key");
    }

    @Test
    void decryptingWithTheWrongKeyFails() {
        String stored = converterWithKey(randomKey()).convertToDatabaseColumn("a-secret");
        EncryptedStringConverter wrongKey = converterWithKey(randomKey());

        assertThatThrownBy(() -> wrongKey.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
    }
}
