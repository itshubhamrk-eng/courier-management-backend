package com.courier.modules.pdp.api;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import com.courier.modules.pdp.domain.PdpStatementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Wire contract of {@code /api/v1/pdp-earnings} and {@code /api/v1/pdp-statements}. */
public final class PdpBillingDtos {

    private PdpBillingDtos() {
    }

    public record EarningResponse(
            UUID id, UUID branchId, String branchCode, String branchName, PdpEarningType earningType, UUID sourceId,
            String referenceNo, LocalDate earnedOn, PdpEarningStatus status, String pincode, UUID districtId,
            BigDecimal actualWeightKg, BigDecimal chargeableWeightKg, BigDecimal ratePerKg, BigDecimal weightCharge,
            BigDecimal mathadiCharge, BigDecimal odaCharge, BigDecimal totalAmount, String remark, UUID statementId) {
    }

    public record GenerateRequest(@NotNull UUID branchId, @NotNull LocalDate from, @NotNull LocalDate to,
                                  @Size(max = 500) String remarks) {
    }

    public record PayRequest(@NotNull LocalDate paidOn, @Size(max = 100) String reference) {
    }

    public record StatementResponse(
            UUID id, String statementNo, UUID branchId, String branchCode, String branchName, LocalDate periodFrom,
            LocalDate periodTo, PdpStatementStatus status, int pickupCount, int deliveryCount, BigDecimal pickupTotal,
            BigDecimal deliveryTotal, BigDecimal netPayable, Instant approvedAt, LocalDate paidOn, String paymentRef,
            String remarks, Instant createdDate, Long version) {
    }

    /** {@code lines} is empty in list responses; {@code unratedInPeriod} counts earnings the statement could not include. */
    public record StatementDetailResponse(StatementResponse statement, List<EarningResponse> lines, long unratedInPeriod) {
    }

    public record FrequencyRequest(@NotNull BillingFrequency frequency) {
    }

    /** {@code dueFrom}/{@code dueTo} are set when a scheduled statement is waiting to be drafted. */
    public record ScheduleResponse(UUID branchId, String branchCode, String branchName, BillingFrequency frequency,
                                   LocalDate lastBilledTo, long unbilledEarnings, LocalDate dueFrom, LocalDate dueTo,
                                   boolean due) {
    }

    public record GenerateDueResponse(int created) {
    }
}
