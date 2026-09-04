package com.courier.modules.shipment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One branch's Vendor Audit totals over a shipment search — reconciliation figures for a
 * franchise/vendor branch. Booking-side figures (paid/to-pay volumes, booking commission,
 * ODA, other charges, cancellations) are grouped by {@link Shipment#getBookingBranchId()};
 * delivery-side figures (delivered count, delivery/DRS commission) are grouped separately
 * by {@link Shipment#getDeliveryBranchId()} — the same split {@code
 * ShipmentBookingWalletListener}/{@code ShipmentDeliveryWalletListener} use to credit two
 * different branch wallets for the same shipment, since a shipment's booking and delivery
 * branch are frequently different. {@code branchId} is any company branch appearing as
 * either role in the search; a branch with no matching shipment in either role does not
 * appear.
 *
 * <p><b>Known limitation, stated plainly:</b> {@code deliveryCommission}/{@code
 * deliveryTotalCommission} are recomputed at report time from the delivery branch's
 * <i>current</i> {@code drsChargePerQty} — the actual system computes and credits DRS
 * commission once, at delivery time, using the rate in effect that moment ({@code
 * ShipmentServiceImpl.deliver}), then never revisits it. If a branch's {@code
 * drsChargePerQty} has changed since an older delivery, this report's figure for that
 * shipment will differ from what was actually credited to the branch wallet. For an exact
 * match to credited amounts, reconcile against {@code WalletTransaction} rows instead.
 *
 * @param paidCommission     booking commission ({@code ShipmentCharge.totalCommission})
 *                           earned on this branch's own PAID-mode bookings only
 * @param deliveryCommission DRS commission earned on shipments this branch delivered —
 *                           identical to {@code deliveryTotalCommission} by definition
 *                           (DRS commission is only ever earned on delivered shipments);
 *                           both are reported since the brief asked for each by name
 * @param bookingTotalCommission total booking commission across every payment mode this
 *                           branch booked (paid, to-pay, and any other configured mode)
 * @param deliveryTotalCommission total DRS commission across every shipment this branch
 *                           delivered — see {@code deliveryCommission}
 */
public record VendorAuditRow(
        UUID branchId,
        long paidOrderCount,
        long paidOrderQuantity,
        BigDecimal paidOrderAmount,
        long topayOrderCount,
        long topayOrderQuantity,
        BigDecimal topayOrderAmount,
        BigDecimal paidCommission,
        BigDecimal deliveryCommission,
        long totalBookedOrderCount,
        long totalDeliveredOrderCount,
        BigDecimal bookingTotalCommission,
        BigDecimal deliveryTotalCommission,
        BigDecimal odaCharges,
        BigDecimal otherCharges,
        long cancelledOrderCount
) {
}
