package com.courier.modules.shipment.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published {@code AFTER_COMMIT} only — a shipment's own transaction has already
 * succeeded by the time any of these fire, the same discipline
 * {@code finance.application.event.WalletEvent} follows for money movements.
 */
public sealed interface ShipmentEvent {

    UUID companyId();

    Instant occurredAt();

    /**
     * A PREPAID shipment was booked and its booking branch's wallet still needs debiting.
     * Published only when the payment mode collects at booking — see
     * {@code ShipmentServiceImpl.create}. Handled by
     * {@code ShipmentBookingWalletListener}, which calls {@code WalletService
     * .debitForBooking} — the seam {@code MEMORY/modules/branch-wallet.md} documented as
     * deliberately not built ahead of this, its consumer.
     *
     * <p>Carries no commission any more — branch commission is credited on
     * {@link DispatchCommissionEarned} instead, once the shipment's Trip Challan
     * (manifest dispatch) is created, not at booking time.
     */
    record PrepaidBookingConfirmed(
            UUID shipmentId,
            UUID companyId,
            UUID bookingBranchId,
            String shipmentNumber,
            BigDecimal netAmount,
            Instant occurredAt
    ) implements ShipmentEvent {
    }

    /**
     * A shipment's Trip Challan was created (its manifest was dispatched) and its booking
     * branch has commission still to collect. Published from {@code
     * ShipmentServiceImpl.transitionToDispatched}, one event per shipment, only when the
     * shipment's payment mode collects at booking and its booking branch has {@code
     * instantCommission} on. Handled by {@code ShipmentBookingWalletListener}, which calls
     * {@code WalletService.creditCommission} — moved here (was on {@link
     * PrepaidBookingConfirmed}, i.e. at booking) on direct user request: "credit branch
     * commission after Trip Challan created", not at order booking.
     *
     * @param branchCommission the branch's own two commission lines, summed —
     *                         {@code commissionOnBasicFreight + branchCommissionOnOtherAmount}
     *                         (V28), deliberately <b>not</b> the shipment charge's stored
     *                         {@code totalCommission}, which also folds in the company's own
     *                         {@code companyCommissionOnBasicFreight} — that is company
     *                         revenue and must never land in the branch's wallet.
     */
    record DispatchCommissionEarned(
            UUID shipmentId,
            UUID companyId,
            UUID bookingBranchId,
            String shipmentNumber,
            BigDecimal branchCommission,
            Instant occurredAt
    ) implements ShipmentEvent {
    }

    /**
     * A shipment collected at delivery ({@code TO_PAY}/{@code COD}) was delivered and its
     * delivery branch's wallet still needs debiting — the delivery-side mirror of
     * {@link PrepaidBookingConfirmed}. Published only when the payment mode collects at
     * delivery — see {@code ShipmentServiceImpl.deliver}. Handled by
     * {@code ShipmentDeliveryWalletListener}, which calls {@code WalletService
     * .debitForCodDelivery}.
     */
    record CodCollectedAtDelivery(
            UUID shipmentId,
            UUID companyId,
            UUID deliveryBranchId,
            String shipmentNumber,
            BigDecimal netAmount,
            Instant occurredAt
    ) implements ShipmentEvent {
    }

    /**
     * A shipment was delivered and its delivery branch's wallet still needs crediting with
     * DRS commission ({@code drsCharge = delivery branch's own drsChargePerQty * item
     * quantity}) — published on every delivery, unlike {@link CodCollectedAtDelivery} which
     * only fires for collect-at-delivery payment modes. See {@code
     * ShipmentServiceImpl.deliver}. Handled by {@code ShipmentDeliveryWalletListener}, which
     * calls {@code WalletService.creditForDrsCharge}. Not published when {@code drsCharge} is
     * zero (a branch with {@code drsChargePerQty} set to 0) — nothing to credit.
     */
    record DrsChargeApplicable(
            UUID shipmentId,
            UUID companyId,
            UUID deliveryBranchId,
            String shipmentNumber,
            BigDecimal drsCharge,
            Instant occurredAt
    ) implements ShipmentEvent {
    }

    // ---------------------------------------------------------------- Communication Center
    //
    // The six records below carry only ids/scalars, same discipline as every event above —
    // com.courier.modules.communication's own listener re-reads full shipment detail at
    // dispatch time rather than trusting a copy that could go stale by the time a retry
    // re-processes it minutes later. This module has no idea that listener exists; it just
    // publishes what happened. See ShipmentServiceImpl's six call sites (create/cancel/
    // transitionToDispatched/scanOneIn/assignOneOutForDelivery/deliver) and
    // com.courier.modules.communication.application.ShipmentCommunicationListener.
    //
    // No RTO_INITIATED/RTO_DELIVERED equivalents exist here — this codebase has no
    // return-to-origin flow yet (only a generic RETURNED terminal state nothing writes, per
    // ShipmentStatus's own doc). CommunicationEventType still declares both for architecture
    // readiness; nothing publishes into them until a real RTO module exists.

    record Booked(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }

    record Dispatched(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }

    /** Published when a shipment reaches its own final destination branch — not on an
     *  intermediate crossing-hub in-scan (see {@code ShipmentServiceImpl.scanOneIn}'s
     *  {@code finalDestination} branch), since a crossing hop is not the customer-facing
     *  "your shipment arrived at the branch" moment the brief's {@code SHIPMENT_RECEIVED}
     *  describes. */
    record ReceivedAtBranch(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }

    record OutForDelivery(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }

    record Delivered(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }

    record Cancelled(UUID shipmentId, UUID companyId, Instant occurredAt) implements ShipmentEvent {
    }
}
