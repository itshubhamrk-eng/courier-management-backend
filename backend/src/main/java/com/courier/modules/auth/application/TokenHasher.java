package com.courier.modules.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of opaque, single-use secrets (reset and verification
 * links) and of refresh tokens before storage.
 *
 * <p>SHA-256 rather than BCrypt here, deliberately. BCrypt's slowness defends
 * low-entropy human passwords against offline guessing. These tokens carry 256 bits
 * of {@code SecureRandom} entropy, so guessing is already infeasible and a slow hash
 * would only add latency to every refresh — on the hot path of every session.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenHasher() {
    }

    /** URL-safe, unpadded — it is going into a link. */
    public static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex-encoded SHA-256, matching the {@code CHAR(64)} storage column. */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Constant-time comparison, for the rare paths that compare hashes in memory
     * rather than via an indexed lookup.
     */
    public static boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
