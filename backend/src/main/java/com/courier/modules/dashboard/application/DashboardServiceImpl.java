package com.courier.modules.dashboard.application;

import com.courier.modules.dashboard.api.dto.DashboardStatisticsResponse;
import com.courier.modules.dashboard.api.dto.DashboardSummaryResponse;
import com.courier.modules.dashboard.api.dto.RecentShipmentResponse;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs the operational dashboard's summary card. Every figure is a real aggregate over
 * {@code Shipment}/{@code ShipmentCharge} — company-scoped by the standard Hibernate
 * filter, so a platform-level caller (no company bound) sees the cross-company total the
 * same way every other company-owned repository behaves for that caller.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Set<ShipmentStatus> IN_TRANSIT = EnumSet.of(
            ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY);

    private static final Set<ShipmentStatus> PENDING = EnumSet.of(
            ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST, ShipmentStatus.MANIFEST_CREATED);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository shipmentChargeRepository;
    private final WalletService walletService;

    /**
     * Deliberately not {@code @Transactional}: {@link #ownWalletBalance} calls into
     * {@code WalletServiceImpl.getForBranch}, its own {@code @Transactional} proxy, which
     * marks a shared transaction rollback-only the moment it throws — before the catch
     * here ever runs. Wrapping this method would turn that caught, expected exception
     * into an {@code UnexpectedRollbackException} on commit. Each repository call below
     * already runs in its own implicit transaction (Spring Data's default), which is all
     * a set of independent point-in-time counts needs.
     */
    @Override
    public DashboardSummaryResponse summary() {
        LocalDate today = LocalDate.now();

        long todayShipments = shipmentRepository.countByBookingDate(today);
        long delivered = shipmentRepository.countByStatus(ShipmentStatus.DELIVERED);
        long inTransit = shipmentRepository.countByStatusIn(IN_TRANSIT);
        long pending = shipmentRepository.countByStatusIn(PENDING);
        long totalShipments = shipmentRepository.count();
        BigDecimal totalRevenue = shipmentChargeRepository.sumNetAmount();
        BigDecimal todayCollection = shipmentChargeRepository.sumNetAmountForBookingDate(today);

        DashboardStatisticsResponse statistics = new DashboardStatisticsResponse(
                todayShipments, delivered, inTransit, pending, totalRevenue,
                todayShipments, todayCollection, inTransit, totalShipments, ownWalletBalance());

        return new DashboardSummaryResponse(statistics, recentShipments());
    }

    /**
     * The caller's own branch wallet balance, or null for a caller with no own branch
     * (company/platform admins) — {@code WalletService.getForBranch(null)} throws
     * {@link BusinessRuleException} for them, which the dashboard degrades rather than
     * surfaces, the same convention as every other omitted figure in this response.
     */
    private BigDecimal ownWalletBalance() {
        try {
            return walletService.getForBranch(null).getAvailableBalance();
        } catch (BusinessRuleException e) {
            return null;
        }
    }

    private List<RecentShipmentResponse> recentShipments() {
        List<Shipment> recent = shipmentRepository.findTop5ByOrderByCreatedAtDesc();
        List<UUID> ids = recent.stream().map(Shipment::getId).toList();
        Map<UUID, BigDecimal> amountByShipmentId = shipmentChargeRepository.findByShipmentIdIn(ids).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, ShipmentCharge::getNetAmount));

        return recent.stream()
                .map((Shipment s) -> new RecentShipmentResponse(
                        s.getId(),
                        s.getTrackingNumber(),
                        s.getReceiverName(),
                        s.getDeliveryPincode(),
                        s.getStatus().name(),
                        s.getCreatedAt(),
                        amountByShipmentId.get(s.getId())))
                .collect(Collectors.toList());
    }
}
