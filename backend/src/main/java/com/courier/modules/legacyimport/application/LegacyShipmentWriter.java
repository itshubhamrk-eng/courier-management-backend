package com.courier.modules.legacyimport.application;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes one legacy row as a history-only shipment, in its own transaction so one bad row
 * never rolls back the rest of the file.
 *
 * <p>Deliberately bypasses {@code ShipmentServiceImpl.create()}: no pricing, no wallet debit,
 * no commission, no events, no customer master rows, and no {@code Shipment.applyInvariants()}
 * (the export has no addresses/pincodes/contacts, so placeholders are stored instead).
 * {@code podApproved} and {@code commissionCredited} are set so no later job ever treats the
 * row as work to do.
 */
@Component
@RequiredArgsConstructor
public class LegacyShipmentWriter {

    static final String ADDRESS_PLACEHOLDER = "N/A (legacy import)";
    static final String CONTACT_PLACEHOLDER = "0000000000";
    static final String PINCODE_PLACEHOLDER = "000000";
    static final BigDecimal DECLARED_VALUE_PLACEHOLDER = new BigDecimal("1.00");
    /** {@code ck_shipments_actual_weight} / {@code _chargeable_weight} require > 0, but the old
     *  software exported 0 for some rows — stored as the smallest weight the column holds. */
    static final BigDecimal MIN_WEIGHT_PLACEHOLDER = new BigDecimal("0.001");

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final ShipmentStatusHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID insert(LegacyRow row, UUID branchId, UUID serviceTypeId, UUID paymentModeId, UUID batchId) {
        UUID companyId = CompanyContext.requireCompanyId();
        BigDecimal weight = row.weight().signum() > 0 ? row.weight() : MIN_WEIGHT_PLACEHOLDER;
        Shipment shipment = Shipment.builder()
                .shipmentNumber(row.consignmentNo())
                .trackingNumber(row.consignmentNo())
                .bookingDate(row.date())
                .bookingBranchId(branchId)
                .currentLocationId(branchId)
                .fromCity(cut(row.from(), 120))
                .toCity(cut(row.to(), 120))
                .pickupPincode(PINCODE_PLACEHOLDER)
                .deliveryPincode(PINCODE_PLACEHOLDER)
                .senderName(orNa(row.consignor(), 150))
                .senderAddress(ADDRESS_PLACEHOLDER)
                .senderContact(CONTACT_PLACEHOLDER)
                .receiverName(orNa(row.consignee(), 150))
                .receiverAddress(ADDRESS_PLACEHOLDER)
                .receiverContact(CONTACT_PLACEHOLDER)
                .serviceTypeId(serviceTypeId)
                .paymentModeId(paymentModeId)
                .expectedDeliveryDate(row.date())
                .actualWeight(weight)
                .chargeableWeight(weight)
                .declaredValue(row.total().signum() > 0 ? row.total() : DECLARED_VALUE_PLACEHOLDER)
                .numberOfPackages(row.packages())
                .status(ShipmentStatus.DELIVERED)
                .podApproved(true)
                .commissionCredited(true)
                .remarks(row.weight().signum() > 0 ? "Imported from legacy system"
                        : "Imported from legacy system (weight not recorded in old software)")
                .legacyImport(true)
                .legacyBatchId(batchId)
                .build();
        Shipment saved = shipmentRepository.save(shipment);

        ShipmentCharge charge = ShipmentCharge.builder()
                .shipmentId(saved.getId())
                .freight(row.total())
                .netAmount(row.total())
                .build();
        chargeRepository.save(charge);

        ShipmentStatusHistory history = ShipmentStatusHistory.builder()
                .shipmentId(saved.getId())
                .branchId(branchId)
                .status(ShipmentStatus.DELIVERED)
                .previousStatus(null)
                .remarks("Imported from legacy system (batch %s)".formatted(batchId))
                .changedBy(SecurityUtils.getCurrentUserId().orElse(null))
                .changedAt(Instant.now())
                .build();
        history.setCompanyId(companyId);
        historyRepository.save(history);
        return saved.getId();
    }

    private static String orNa(String value, int max) {
        return value == null || value.isBlank() ? "N/A" : cut(value.trim(), max);
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
