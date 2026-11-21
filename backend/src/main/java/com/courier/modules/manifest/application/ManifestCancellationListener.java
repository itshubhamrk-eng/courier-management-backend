package com.courier.modules.manifest.application;

import com.courier.modules.shipment.application.event.ShipmentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * When a shipment is cancelled it has already been taken off its manifest creation; if that was the
 * sheet's last shipment the sheet is cancelled too. Synchronous on purpose: it joins the cancel
 * transaction, so a failure here rolls the shipment cancellation back instead of leaving an empty
 * manifest creation behind (the original bug).
 */
@Component
@RequiredArgsConstructor
public class ManifestCancellationListener {

    private final ManifestService manifestService;

    @EventListener
    public void on(ShipmentEvent.Cancelled event) {
        if (event.manifestId() != null) {
            manifestService.cancelIfEmpty(event.manifestId());
        }
    }
}
