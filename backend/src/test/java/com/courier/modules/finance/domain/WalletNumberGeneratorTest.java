package com.courier.modules.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Shape and collision behaviour of the generated identifiers. */
class WalletNumberGeneratorTest {

    private static final Instant AT = Instant.parse("2026-07-28T10:15:30Z");

    @Test
    @DisplayName("a wallet number is WLT + yyMM + 8 characters and fits the column")
    void walletNumberShape() {
        String number = WalletNumberGenerator.walletNumber(AT);

        assertThat(number).startsWith("WLT2607").hasSize(15);
        assertThat(number.length()).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("a transaction number is TXN + yyyyMMdd + 10 characters and fits the column")
    void transactionNumberShape() {
        String number = WalletNumberGenerator.transactionNumber(AT);

        assertThat(number).startsWith("TXN20260728").hasSize(21);
        assertThat(number.length()).isLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("the alphabet excludes characters that are misread aloud")
    void unambiguousAlphabet() {
        String suffix = WalletNumberGenerator.walletNumber(AT).substring(7);

        assertThat(suffix).doesNotContain("I").doesNotContain("O")
                .doesNotContain("0").doesNotContain("1");
    }

    @Test
    @DisplayName("ten thousand numbers in the same month do not collide")
    void noCollisionsInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(WalletNumberGenerator.walletNumber(AT));
        }
        assertThat(seen).hasSize(10_000);
    }
}
