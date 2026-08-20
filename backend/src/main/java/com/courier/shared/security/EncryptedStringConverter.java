package com.courier.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA converter for a column that must hold ciphertext, not plaintext — e.g. a company's
 * own Razorpay key secret ({@code CompanyRazorpayConfig}).
 *
 * <p>AES-256-GCM, a fresh random 12-byte IV per value (GCM must never reuse an IV under the
 * same key). Stored as {@code Base64(IV || ciphertext+tag)} — one column, self-contained,
 * no separate IV column to keep in sync.
 *
 * <p>Not {@code autoApply}: a converter this consequential is opted into explicitly, per
 * field, with {@code @Convert(converter = EncryptedStringConverter.class)} — the same
 * reasoning {@code PaymentGatewayConfig} gives for not using
 * {@code @ConditionalOnMissingBean}: an implicit, easy-to-miss default is the wrong shape
 * for something a silent mistake would turn into a plaintext secret in the database.
 *
 * <p>Fails closed at the point of use: encrypting or decrypting without
 * {@code app.security.encryption-key} configured throws, rather than falling back to
 * storing plaintext. This is deliberately a runtime failure, not a startup one — most
 * deployments never persist a secret through this converter, so requiring the key at
 * application boot would force every deployment to set it "just in case".
 *
 * <p>Registered as a Spring bean ({@code @Component}) rather than left for Hibernate to
 * instantiate directly: Spring Boot wires JPA converters through Hibernate's Spring bean
 * container automatically, which is what lets {@link SecretsEncryptionProperties} be
 * injected here instead of read some other way.
 */
@Component
@RequiredArgsConstructor
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretsEncryptionProperties properties;

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            SecretKeySpec key = key();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            // Never fall back to storing the plaintext — a broken cipher must fail loudly.
            throw new IllegalStateException(
                    "Could not encrypt a value for storage. Check app.security.encryption-key "
                            + "(SECRETS_ENCRYPTION_KEY) is a valid base64 32-byte AES key.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not decrypt a stored value. Check app.security.encryption-key "
                            + "(SECRETS_ENCRYPTION_KEY) matches the key it was encrypted with.", e);
        }
    }

    private SecretKeySpec key() {
        String encoded = properties.getEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "app.security.encryption-key (SECRETS_ENCRYPTION_KEY) is not set. A secret "
                            + "cannot be stored or read until it is. Generate one with: "
                            + "openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
}
