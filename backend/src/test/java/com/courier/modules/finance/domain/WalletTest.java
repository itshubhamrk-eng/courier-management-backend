package com.courier.modules.finance.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The wallet's own rules: money in, money out, and the things it refuses. */
class WalletTest {

    private Wallet wallet(String available, String hold, WalletStatus status) {
        return Wallet.builder()
                .walletNumber("WLT2607ABCDEFGH")
                .branchId(UUID.randomUUID())
                .status(status)
                .availableBalance(new BigDecimal(available))
                .holdBalance(new BigDecimal(hold))
                .currency("INR")
                .build();
    }

    private Wallet active(String available) {
        return wallet(available, "0", WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("credit raises the available balance and returns it")
    void credit() {
        Wallet w = active("100.00");

        BigDecimal after = w.applyCredit(new BigDecimal("250.50"));

        assertThat(after).isEqualByComparingTo("350.50");
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("350.50");
    }

    @Test
    @DisplayName("debit lowers the available balance and returns it")
    void debit() {
        Wallet w = active("500.00");

        BigDecimal after = w.applyDebit(new BigDecimal("120.25"));

        assertThat(after).isEqualByComparingTo("379.75");
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("379.75");
    }

    @Test
    @DisplayName("a debit beyond the available balance is refused, and changes nothing")
    void debitInsufficient() {
        Wallet w = active("100.00");

        assertThatThrownBy(() -> w.applyDebit(new BigDecimal("100.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient wallet balance");

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("held money is not spendable")
    void holdIsNotSpendable() {
        Wallet w = wallet("100.00", "400.00", WalletStatus.ACTIVE);

        assertThat(w.getTotalBalance()).isEqualByComparingTo("500.00");
        assertThatThrownBy(() -> w.applyDebit(new BigDecimal("200.00")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a debit down to exactly zero is allowed")
    void debitToZero() {
        Wallet w = active("100.0000");

        assertThat(w.applyDebit(new BigDecimal("100.0000"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("zero and negative amounts are refused in both directions")
    void nonPositiveAmounts() {
        Wallet w = active("100.00");

        assertThatThrownBy(() -> w.applyCredit(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> w.applyCredit(new BigDecimal("-5")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> w.applyDebit(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> w.applyCredit(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = {"INACTIVE", "SUSPENDED", "CLOSED"})
    @DisplayName("a wallet that is not ACTIVE accepts nothing")
    void nonOperational(WalletStatus status) {
        Wallet w = wallet("100.00", "0", status);

        assertThatThrownBy(() -> w.applyCredit(new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot accept transactions");
        assertThatThrownBy(() -> w.applyDebit(new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(w.isActive()).isFalse();
    }

    @Test
    @DisplayName("amounts are normalised to the money scale, so 100 and 100.0000 agree")
    void scaleNormalisation() {
        Wallet w = active("100");

        w.applyCredit(new BigDecimal("0.00004"));   // below the stored scale

        // HALF_UP at four decimals: 0.00004 rounds away, the balance is unchanged in value.
        assertThat(w.getAvailableBalance().scale()).isEqualTo(Wallet.MONEY_SCALE);
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("total balance is available plus hold")
    void totalBalance() {
        assertThat(wallet("12.3456", "7.6544", WalletStatus.ACTIVE).getTotalBalance())
                .isEqualByComparingTo("20.0000");
    }
}
