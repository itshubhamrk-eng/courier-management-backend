package com.courier.modules.finance.domain;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates the human-facing wallet and transaction numbers.
 *
 * <p>Not a database sequence, and deliberately not a per-company counter. A counter needs a
 * lock or a row of its own on the hot booking path, and a gapless per-company sequence
 * leaks how much business a company does to anyone who sees two of its numbers. A dated
 * prefix plus random suffix is orderable enough to read, cheap to produce under
 * concurrency, and unguessable.
 *
 * <p>The alphabet drops {@code I O 0 1} so a number read aloud from a receipt or retyped
 * from a screenshot cannot land on the wrong wallet. 8 random characters over 32 symbols
 * is 40 bits; the caller still checks the unique index and retries, because "unlikely" is
 * not "impossible" and the collision must be a retry rather than a 500.
 */
public final class WalletNumberGenerator {

    /** Crockford-ish: no I, O, 0 or 1. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyMM").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String WALLET_PREFIX = "WLT";
    public static final String TRANSACTION_PREFIX = "TXN";

    private WalletNumberGenerator() {
    }

    /** e.g. {@code WLT2607K4M9PX2A} — 15 characters, fits {@code VARCHAR(30)}. */
    public static String walletNumber() {
        return walletNumber(Instant.now());
    }

    static String walletNumber(Instant at) {
        return WALLET_PREFIX + MONTH.format(at) + random(8);
    }

    /** e.g. {@code TXN20260728K4M9PX2AB7} — 21 characters, fits {@code VARCHAR(40)}. */
    public static String transactionNumber() {
        return transactionNumber(Instant.now());
    }

    static String transactionNumber(Instant at) {
        return TRANSACTION_PREFIX + DAY.format(at) + random(10);
    }

    private static String random(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }
}
