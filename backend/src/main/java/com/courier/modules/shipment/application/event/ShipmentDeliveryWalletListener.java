package com.courier.modules.shipment.application.event;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.command.CodDeliveryDebitCommand;
import com.courier.modules.finance.application.command.DrsChargeCreditCommand;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Debits the delivery branch's wallet once a collect-at-delivery shipment's own delivery
 * transaction has committed — the delivery-side mirror of
 * {@link ShipmentBookingWalletListener}, closing the gap the wallet module's {@code COD}
 * sub-transaction type was seeded for but nothing ever triggered. Also credits the delivery
 * branch's wallet with its DRS commission on every delivery.
 *
 * <p>Same AFTER_COMMIT/{@code REQUIRES_NEW} shape: the shipment is already DELIVERED and
 * durable when this runs, so a failure (insufficient balance for the COD debit, a wallet
 * that went INACTIVE mid-flight) cannot roll the delivery back. The shipment stays
 * DELIVERED, un-posted; a real, logged gap in the same shape {@code
 * ShipmentBookingWalletListener} already accepts for the booking side.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentDeliveryWalletListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShipmentEvent.CodCollectedAtDelivery event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> walletService.debitForCodDelivery(
                    new CodDeliveryDebitCommand(event.deliveryBranchId(), event.netAmount(),
                            event.shipmentNumber(), "COD collected for shipment " + event.shipmentNumber())));
        } catch (RuntimeException e) {
            log.error("Could not debit delivery branch {} for shipment {} ({}); the shipment stays "
                    + "delivered, undebited — reconcile manually", event.deliveryBranchId(),
                    event.shipmentNumber(), event.shipmentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShipmentEvent.DrsChargeApplicable event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> walletService.creditForDrsCharge(
                    new DrsChargeCreditCommand(event.deliveryBranchId(), event.drsCharge(),
                            event.shipmentNumber(), "DRS commission for shipment " + event.shipmentNumber())));
        } catch (RuntimeException e) {
            log.error("Could not credit DRS commission to delivery branch {} for shipment {} ({}); the "
                    + "shipment stays delivered, uncredited — reconcile manually", event.deliveryBranchId(),
                    event.shipmentNumber(), event.shipmentId(), e);
        }
    }
}
