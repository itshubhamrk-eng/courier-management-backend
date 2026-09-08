package com.courier.modules.finance.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What Finance needs to know about commission the booking branch has <em>earned but not yet
 * been credited</em> — the wallet dashboard's forward-looking figures, next to
 * {@code todayCredit}'s already-settled one.
 *
 * <p>Same seam as {@link BranchDirectoryPort}: Finance owns the interface, {@code
 * modules/shipment} supplies the adapter, so this module never imports a {@code Shipment} or
 * {@code ShipmentCharge}.
 *
 * <p>Two figures, one per commission trigger {@code ShipmentBookingWalletListener} listens
 * for: a shipment's branch commission is credited on Trip Challan dispatch for
 * collect-at-booking payment modes, or on delivery for collect-at-delivery (TO_PAY/COD)
 * ones — see {@code ShipmentEvent.DispatchCommissionEarned} / {@code
 * DeliveryCommissionEarned} for why. Everything still short of that trigger is "pending".
 */
public interface PendingCommissionPort {

    record PendingCommission(BigDecimal bookingPending, BigDecimal deliveryPending) {
        public static final PendingCommission ZERO = new PendingCommission(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * @param bookingPending commission on collect-at-booking shipments still short of
     *                       dispatch (statuses before {@code DISPATCHED})
     * @param deliveryPending commission on collect-at-delivery (TO_PAY/COD) shipments still
     *                        short of delivery (every status before {@code DELIVERED})
     * @return {@link PendingCommission#ZERO} for a branch with {@code instantCommission} off,
     *         or with nothing open
     */
    PendingCommission pendingCommissionFor(UUID bookingBranchId, UUID companyId);
}
