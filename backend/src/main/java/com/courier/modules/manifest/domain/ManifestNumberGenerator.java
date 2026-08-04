package com.courier.modules.manifest.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;

/**
 * {@code MFT-yyMMdd-XXXX} — a date-scoped prefix plus a 4-digit random suffix, retried by
 * the caller on a unique-index collision (the same existence-check-and-retry contract
 * {@code finance.domain.WalletNumberGenerator} uses, not {@code MAX(...)+1}).
 */
public final class ManifestNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int LOWER_BOUND = 1000;
    private static final int UPPER_BOUND = 10000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ManifestNumberGenerator() {
    }

    public static String generate() {
        return "MFT-" + LocalDate.now().format(DATE) + "-"
                + (LOWER_BOUND + RANDOM.nextInt(UPPER_BOUND - LOWER_BOUND));
    }
}
