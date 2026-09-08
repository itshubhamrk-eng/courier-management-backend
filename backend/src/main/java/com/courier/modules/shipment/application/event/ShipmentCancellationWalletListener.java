package com.courier.modules.shipment.application.event;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.command.ShipmentReversalCommand;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Undoes whatever money already moved for a shipment once it is cancelled — freight debited
 * at booking ({@link ShipmentBookingWalletListener#on(ShipmentEvent.PrepaidBookingConfirmed)})
 * credited back, and any commission already earned debited back, on whichever branch's wallet
 * each entry actually sits on. See {@code WalletService.reverseForShipment} for the reversal
 * rule itself; this listener only wires the shipment-side trigger to it.
 *
 * <p>Same AFTER_COMMIT/{@code REQUIRES_NEW} shape as the module's other wallet listeners: the
 * shipment is already CANCELLED and durable when this runs, so a reversal failure cannot roll
 * the cancellation back. The shipment stays CANCELLED, un-reversed — a real, logged gap in the
 * same shape {@link ShipmentBookingWalletListener} already accepts for the booking side.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentCancellationWalletListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShipmentEvent.Cancelled event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> walletService.reverseForShipment(
                    new ShipmentReversalCommand(event.shipmentNumber(),
                            "Shipment " + event.shipmentNumber() + " cancelled")));
        } catch (RuntimeException e) {
            log.error("Could not reverse wallet entries for cancelled shipment {} ({}); "
                    + "reconcile manually", event.shipmentNumber(), event.shipmentId(), e);
        }
    }
}
