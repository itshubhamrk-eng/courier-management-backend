package com.courier.modules.customerpricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The per-zone rate card of a business customer. {@code COMPANY_ADMIN} writes; authenticated users read and calculate. */
public interface ZoneRateService {

    /** Creates the zone's rate card, or fully replaces it (header and slabs) if one exists. */
    ZoneRateView save(UUID zoneId, SaveCommand command);

    /** Empty if the zone has no rate card yet. */
    java.util.Optional<ZoneRateView> getByZone(UUID zoneId);

    /** Every rate card of the company's customers, optionally for one customer. */
    List<ZoneRateView> search(UUID customerId);

    ZoneRateView activate(UUID zoneId);

    ZoneRateView deactivate(UUID zoneId);

    void delete(UUID zoneId);

    /** Prices a shipment for a customer and destination from the matching zone's rate card. */
    Quote calculate(QuoteCommand command);

    record SaveCommand(BigDecimal minChargeableWeight, BigDecimal docketCharge, BigDecimal mathadiCharge,
                       BigDecimal packCharge, BigDecimal odaCharge, BigDecimal fuelSurchargePercent,
                       BigDecimal fovPercent, LocalDate effectiveFrom, LocalDate effectiveTo,
                       List<SlabInput> slabs, Long expectedVersion) {
    }

    record SlabInput(BigDecimal minWeight, BigDecimal maxWeight, BigDecimal ratePerKg) {
    }

    record QuoteCommand(UUID customerId, UUID districtId, UUID cityId, BigDecimal actualWeight,
                        BigDecimal declaredValue, boolean odaApplicable, LocalDate bookingDate) {
    }

    record Quote(UUID zoneId, String zoneName, BigDecimal actualWeight, BigDecimal chargeableWeight,
                 BigDecimal ratePerKg, BigDecimal freight, BigDecimal docketCharge, BigDecimal mathadiCharge,
                 BigDecimal packCharge, BigDecimal odaCharge, BigDecimal fuelSurcharge, BigDecimal fovCharge,
                 BigDecimal total) {
    }
}
