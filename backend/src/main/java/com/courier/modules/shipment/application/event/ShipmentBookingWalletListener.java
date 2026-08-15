package com.courier.modules.shipment.application.event;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.command.BookingDebitCommand;
import com.courier.modules.finance.application.command.CommissionCreditCommand;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Debits the booking branch's wallet once a PREPAID shipment's own booking transaction has
 * committed — the brief's own "Debit wallet only after successful booking transaction",
 * and the "Booking debit seam" {@code MEMORY/modules/branch-wallet.md} left open for this
 * module to close.
 *
 * <p>AFTER_COMMIT and {@code REQUIRES_NEW}, the same shape
 * {@code finance.application.WalletProvisioningListener} uses: the shipment is already
 * durable when this runs, so a debit failure (an insufficient balance the pre-booking
 * check missed under a race, or a wallet that went INACTIVE mid-flight) cannot roll the
 * booking back. The shipment stays BOOKED, undebited; this is a real, logged gap in the
 * exact same shape the wallet-provisioning race already accepts, not swept under
 * "impossible" — see the module's own memory doc for the follow-up this implies.
 *
 * <p>Also credits branch commission, but on a separate event — {@link
 * ShipmentEvent.DispatchCommissionEarned}, fired once the shipment's Trip Challan (manifest
 * dispatch) is created, not at booking time; see that event's own javadoc for why.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentBookingWalletListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShipmentEvent.PrepaidBookingConfirmed event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> walletService.debitForBooking(
                    new BookingDebitCommand(event.bookingBranchId(),
                            event.netAmount(), event.shipmentNumber(),
                            "Freight for shipment " + event.shipmentNumber())));
        } catch (RuntimeException e) {
            log.error("Could not settle wallet debit for branch {}, shipment {} ({}); "
                    + "the shipment stays booked, undebited — reconcile manually",
                    event.bookingBranchId(), event.shipmentNumber(), event.shipmentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShipmentEvent.DispatchCommissionEarned event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> walletService.creditCommission(
                    new CommissionCreditCommand(event.bookingBranchId(),
                            event.branchCommission(), event.shipmentNumber(),
                            "Branch commission for shipment " + event.shipmentNumber())));
        } catch (RuntimeException e) {
            log.error("Could not settle commission credit for branch {}, shipment {} ({}); "
                    + "the manifest stays dispatched, commission uncredited — reconcile manually",
                    event.bookingBranchId(), event.shipmentNumber(), event.shipmentId(), e);
        }
    }
}
