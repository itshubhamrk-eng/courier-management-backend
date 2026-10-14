package com.courier.modules.pdp.application;

import com.courier.modules.pdp.domain.PdpRateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Pickup / delivery rate cards of Pickup-Delivery Partners. {@code COMPANY_ADMIN} writes; authenticated users read and calculate. */
public interface PdpRateService {

    PdpRateView create(SaveCommand command);

    /** Full replacement of header and slabs. */
    PdpRateView update(UUID id, SaveCommand command);

    PdpRateView get(UUID id);

    /** Every card, optionally for one partner and/or one type. */
    List<PdpRateView> search(UUID branchId, PdpRateType rateType);

    PdpRateView activate(UUID id);

    PdpRateView deactivate(UUID id);

    void delete(UUID id);

    /**
     * What a partner is paid for one job: the partner's district-specific card if one is
     * live on the date, else its all-districts card. Throws when neither exists — there is
     * deliberately no fallback to the branch commission fields.
     */
    Quote calculate(QuoteCommand command);

    /**
     * The same pricing as {@link #calculate} without the caller-scope check — for the earning
     * flows, which price a partner's job on behalf of whoever triggered it (a company user
     * approving a POD, a hub scanning). Internal: not exposed over HTTP.
     */
    Quote quote(QuoteCommand command);

    record SaveCommand(UUID branchId, PdpRateType rateType, UUID districtId, BigDecimal minChargeableWeight,
                       BigDecimal mathadiCharge, BigDecimal odaCharge, BigDecimal failedAttemptFee,
                       BigDecimal returnFee, LocalDate effectiveFrom, LocalDate effectiveTo,
                       List<SlabInput> slabs, Long expectedVersion) {
    }

    record SlabInput(BigDecimal minWeight, BigDecimal maxWeight, BigDecimal ratePerKg) {
    }

    record QuoteCommand(UUID branchId, PdpRateType rateType, UUID districtId, BigDecimal actualWeight,
                        boolean odaApplicable, LocalDate date) {
    }

    record Quote(UUID rateId, PdpRateType rateType, BigDecimal actualWeight, BigDecimal chargeableWeight,
                 BigDecimal ratePerKg, BigDecimal weightCharge, BigDecimal mathadiCharge, BigDecimal odaCharge,
                 BigDecimal total) {
    }
}
