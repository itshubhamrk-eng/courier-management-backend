package com.courier.shared.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The master key for {@link EncryptedStringConverter}, bound from {@code app.security.*}.
 *
 * <p>No default — same rule as {@code app.jwt.secret}: the app must not be able to encrypt
 * anything with a key that shipped in the repo. A deployment that never stores a secret in
 * the database (no company has configured its own Razorpay account, say) never needs this
 * set; {@link EncryptedStringConverter} only fails when it is actually asked to encrypt or
 * decrypt a value.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecretsEncryptionProperties {

    /** Base64-encoded AES-256 key. Generate with {@code openssl rand -base64 32}. */
    private String encryptionKey;
}
