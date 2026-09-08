package com.courier.modules.shipment.infrastructure;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.finance.domain.PendingCommissionPort;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Answers Finance's pending-commission question, backed by {@code shipments} and {@code
 * shipment_charges}. Same seam as {@code CompanyBranchDirectory}: Finance owns {@link
 * PendingCommissionPort}, this module supplies the adapter — direct repository reads, no
 * {@code @PreAuthorize}, because the caller's own endpoint already authorised the read this
 * composes into.
 *
 * <p>Mirrors {@code ShipmentServiceImpl.eligibleBranchCommission} exactly (same two charge
 * lines, same {@code instantCommission} gate) so a shipment's pending figure here and its
 * eventual credited amount there never disagree.
 */
@Component
@RequiredArgsConstructor
public class ShipmentPendingCommissionDirectory implements PendingCommissionPort {

    /** Statuses a collect-at-booking shipment sits in before {@code DISPATCHED} — the
     *  status {@code transitionToDispatched} moves it to and fires {@code
     *  DispatchCommissionEarned} from. */
    private static final Set<ShipmentStatus> PRE_DISPATCH =
            EnumSet.of(ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST, ShipmentStatus.MANIFEST_CREATED);

    /** Every status short of {@code DELIVERED} — where a collect-at-delivery shipment's
     *  commission fires. Superset of {@link #PRE_DISPATCH}, queried once and filtered
     *  twice below rather than two separate repository calls. */
    private static final Set<ShipmentStatus> PRE_DELIVERY =
            EnumSet.of(ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST, ShipmentStatus.MANIFEST_CREATED,
                    ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final BranchService branchService;
    private final PaymentModeService paymentModeService;

    @Override
    @Transactional(readOnly = true)
    public PendingCommission pendingCommissionFor(UUID bookingBranchId, UUID companyId) {
        if (bookingBranchId == null || companyId == null
                || !branchService.instantCommissionOf(bookingBranchId)) {
            return PendingCommission.ZERO;
        }

        List<Shipment> open = shipmentRepository.findAllByCompanyIdAndBookingBranchIdAndStatusIn(
                companyId, bookingBranchId, PRE_DELIVERY);
        if (open.isEmpty()) {
            return PendingCommission.ZERO;
        }

        Map<UUID, ShipmentCharge> charges = chargeRepository
                .findByShipmentIdIn(open.stream().map(Shipment::getId).toList()).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, c -> c));
        Map<UUID, PaymentMode> paymentModes = open.stream()
                .map(Shipment::getPaymentModeId).distinct()
                .collect(Collectors.toMap(id -> id, paymentModeService::getById));

        BigDecimal bookingPending = open.stream()
                .filter(s -> PRE_DISPATCH.contains(s.getStatus()))
                .filter(s -> paymentModes.get(s.getPaymentModeId()).isCollectAtBooking())
                .map(s -> commissionOf(charges.get(s.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryPending = open.stream()
                .filter(s -> paymentModes.get(s.getPaymentModeId()).isCollectAtDelivery())
                .map(s -> commissionOf(charges.get(s.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PendingCommission(bookingPending, deliveryPending);
    }

    private static BigDecimal commissionOf(ShipmentCharge charge) {
        if (charge == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal onFreight = charge.getCommissionOnBasicFreight();
        BigDecimal onOther = charge.getBranchCommissionOnOtherAmount();
        return (onFreight == null ? BigDecimal.ZERO : onFreight)
                .add(onOther == null ? BigDecimal.ZERO : onOther);
    }
}
