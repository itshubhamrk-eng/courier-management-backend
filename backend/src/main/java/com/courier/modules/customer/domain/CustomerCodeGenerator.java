package com.courier.modules.customer.domain;

import java.security.SecureRandom;

/**
 * Generates a customer code when the caller does not supply one — the common case
 * for a counter clerk registering a walk-in customer mid-booking, who has no reason
 * to invent a code by hand.
 *
 * <p>Same alphabet as {@code WalletNumberGenerator}: no {@code I O 0 1}, so a code
 * read off a receipt cannot be misread onto a different customer. The caller still
 * checks the unique index and retries on collision, same as a wallet number.
 */
public final class CustomerCodeGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "CUST";

    private CustomerCodeGenerator() {
    }

    /** e.g. {@code CUST7K4M9PX2} — 12 characters, fits {@code VARCHAR(50)} with room to spare. */
    public static String generate() {
        StringBuilder out = new StringBuilder(PREFIX.length() + 8);
        out.append(PREFIX);
        for (int i = 0; i < 8; i++) {
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }
}
