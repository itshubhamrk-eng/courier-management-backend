package com.courier.modules.pdp.application;

import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Accrued earnings of Pickup-Delivery Partners. The {@code record*} methods are called by the
 * pickup and shipment flows inside their own transaction and never throw for a missing rate
 * card — that becomes an UNRATED earning an admin can re-price. Nothing here touches a wallet.
 */
public interface PdpEarningService {

    /** A pickup reached BOOKED: pays the partner per its pickup rate card. Idempotent per pickup. */
    void recordPickup(UUID branchId, UUID pickupId, String pickupNo, String pincode, BigDecimal weightKg, LocalDate date);

    /** A partner-delivered shipment is DELIVERED with POD approved: pays per its delivery card. Idempotent per shipment. */
    void recordDelivery(UUID branchId, UUID shipmentId, String shipmentNo, String pincode, BigDecimal weightKg, LocalDate date);

    /**
     * The shipment was cancelled: undo what its pickup and delivery earned. An unbilled earning is
     * simply marked REVERSED; one already on a statement stays there and a negative adjustment
     * earning is accrued so the NEXT statement nets it out. Idempotent.
     */
    void reverseForShipment(UUID shipmentId, UUID pickupId);

    Page<PdpEarning> search(UUID branchId, PdpEarningType type, PdpEarningStatus status, LocalDate from, LocalDate to,
                            Pageable pageable);

    /** Prices an UNRATED earning now that a rate card exists. */
    PdpEarning reprice(UUID earningId);
}
