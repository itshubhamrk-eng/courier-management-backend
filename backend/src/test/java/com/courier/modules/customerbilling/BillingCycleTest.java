package com.courier.modules.customerbilling;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.customerbilling.application.BillingCycle;
import com.courier.modules.customerbilling.domain.CustomerInvoice;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingCycleTest {

    @Test
    @DisplayName("weekly closes on the last Sunday before today (Monday -> yesterday, Sunday -> a week earlier)")
    void weekly() {
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.WEEKLY, LocalDate.of(2026, 10, 12))) // Monday
                .isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.WEEKLY, LocalDate.of(2026, 10, 13))) // Tuesday
                .isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.WEEKLY, LocalDate.of(2026, 10, 18))) // Sunday
                .isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    @DisplayName("15-day cycles close on the 15th and the last day of the month")
    void fifteenDays() {
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.FIFTEEN_DAYS, LocalDate.of(2026, 10, 16)))
                .isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.FIFTEEN_DAYS, LocalDate.of(2026, 10, 31)))
                .isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.FIFTEEN_DAYS, LocalDate.of(2026, 11, 1)))
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.FIFTEEN_DAYS, LocalDate.of(2026, 3, 15)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("monthly closes on the last day of the previous month, across a year boundary too")
    void monthly() {
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.MONTHLY, LocalDate.of(2026, 10, 13)))
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(BillingCycle.lastClosedCycleEnd(BillingFrequency.MONTHLY, LocalDate.of(2027, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    private static CustomerInvoice invoice(String total, String prepaid) {
        return CustomerInvoice.builder().totalAmount(new BigDecimal(total)).prepaidAmount(new BigDecimal(prepaid)).build();
    }

    @Test
    @DisplayName("balance = total - prepaid - receipts; status follows it")
    void balanceAndStatus() {
        CustomerInvoice i = invoice("1000.00", "200.00");
        i.refreshStatus();
        assertThat(i.balance()).isEqualByComparingTo("800.00");
        i.applyPayment(new BigDecimal("300"));
        assertThat(i.getStatus().name()).isEqualTo("PARTLY_PAID");
        i.applyPayment(new BigDecimal("500"));
        assertThat(i.getStatus().name()).isEqualTo("PAID");
        assertThat(i.balance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an invoice that is fully prepaid starts PAID; overpayment and zero payment are refused")
    void paymentRules() {
        CustomerInvoice prepaid = invoice("500.00", "500.00");
        prepaid.refreshStatus();
        assertThat(prepaid.getStatus().name()).isEqualTo("PAID");

        CustomerInvoice i = invoice("100.00", "0");
        assertThatThrownBy(() -> i.applyPayment(new BigDecimal("100.01"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> i.applyPayment(BigDecimal.ZERO)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("an invoice with a payment cannot be cancelled; a cancelled one takes no payment")
    void cancelRules() {
        CustomerInvoice paid = invoice("100.00", "0");
        paid.applyPayment(new BigDecimal("10"));
        assertThatThrownBy(paid::cancel).isInstanceOf(BusinessRuleException.class);

        CustomerInvoice open = invoice("100.00", "0");
        open.cancel();
        assertThatThrownBy(() -> open.applyPayment(BigDecimal.ONE)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a new invoice is pending approval and takes no payment until approved")
    void approvalGate() {
        CustomerInvoice i = invoice("100.00", "0");
        assertThat(i.getApprovalStatus().name()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(i::requireApproved).isInstanceOf(BusinessRuleException.class);
        i.approve(java.util.UUID.randomUUID());
        assertThat(i.isApproved()).isTrue();
        assertThat(i.getApprovedAt()).isNotNull();
        i.requireApproved();
    }

    @Test
    @DisplayName("reversing a payment puts it back on the balance and reopens the invoice")
    void reversePayment() {
        CustomerInvoice i = invoice("500.00", "100.00");
        i.applyPayment(new BigDecimal("400"));
        assertThat(i.getStatus().name()).isEqualTo("PAID");
        i.reversePayment(new BigDecimal("400"));
        assertThat(i.getStatus().name()).isEqualTo("UNPAID");
        assertThat(i.balance()).isEqualByComparingTo("400.00");
        i.applyPayment(new BigDecimal("150"));
        i.reversePayment(new BigDecimal("50"));
        assertThat(i.getStatus().name()).isEqualTo("PARTLY_PAID");
        assertThat(i.getAmountPaid()).isEqualByComparingTo("100");
    }
}
