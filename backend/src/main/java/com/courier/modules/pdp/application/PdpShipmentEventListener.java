package com.courier.modules.pdp.application;

import com.courier.modules.shipment.application.event.ShipmentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;
import java.time.ZoneOffset;

/**
 * Accrues a partner's delivery earning when the shipment module says a partner delivery is
 * complete with its POD approved. Synchronous on purpose: it runs in the delivering/approving
 * transaction so the earning and the delivery stand or fall together. It never throws for a
 * missing rate card (that becomes an UNRATED earning), so it cannot block a delivery.
 */
@Component
@RequiredArgsConstructor
class PdpShipmentEventListener {

    private final PdpEarningService earningService;
    private final com.courier.modules.pickup.domain.PickupRequestRepository pickups;

    @EventListener
    void onPartnerDeliveryEarned(ShipmentEvent.PartnerDeliveryEarned e) {
        earningService.recordDelivery(e.partnerBranchId(), e.shipmentId(), e.shipmentNumber(),
                e.deliveryPincode(), e.weightKg(), LocalDate.now(ZoneOffset.UTC));
    }

    /** A cancelled shipment un-earns its pickup (and delivery, if any). */
    @EventListener
    void onCancelled(ShipmentEvent.Cancelled e) {
        UUID pickupId = pickups.findByShipment(e.companyId(), e.shipmentId())
                .map(com.courier.modules.pickup.domain.PickupRequest::getId).orElse(null);
        earningService.reverseForShipment(e.shipmentId(), pickupId);
    }
}
