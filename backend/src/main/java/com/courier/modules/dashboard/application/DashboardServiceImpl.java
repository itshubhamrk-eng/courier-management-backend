package com.courier.modules.dashboard.application;

import com.courier.modules.dashboard.api.dto.DashboardStatisticsResponse;
import com.courier.modules.dashboard.api.dto.DashboardSummaryResponse;
import com.courier.modules.dashboard.api.dto.RecentShipmentResponse;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
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
 * {@code Shipment}/{@code ShipmentCharge}.
 *
 * <p><b>Company scoping is explicit, not left to the implicit Hibernate filter</b> —
 * every query below takes the caller's company id (or {@code null} for a genuinely
 * cross-tenant {@code SUPER_ADMIN} read) as an argument. This method used to rely
 * solely on {@code CompanyFilterAspect} auto-enabling the filter per repository call,
 * which silently did not apply here (root cause not fully isolated — plausibly this
 * method's own deliberate non-{@code @Transactional} shape, see below, putting each
 * repository call on its own short-lived session): any authenticated company user
 * could read every tenant's shipment counts/revenue. Fixed 2026-08-17 — see
 * {@code perf-tests/ISSUES.md} ISSUE-001 in the repo root for the full writeup.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Set<ShipmentStatus> IN_TRANSIT = EnumSet.of(
            ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY);

    private static final Set<ShipmentStatus> PENDING = EnumSet.of(
            ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST, ShipmentStatus.MANIFEST_CREATED);

    /** Mirrors the Pending Delivery page: shipments arrived at (or dispatched for local
     *  delivery from) the caller's own branch, still waiting to go out or be closed. */
    private static final Set<ShipmentStatus> PENDING_DELIVERY = EnumSet.of(
            ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository shipmentChargeRepository;
    private final WalletService walletService;

    /**
     * Deliberately not {@code @Transactional}: {@link #ownWallet} calls into
     * {@code WalletServiceImpl.getForBranch}, its own {@code @Transactional} proxy, which
     * marks a shared transaction rollback-only the moment it throws — before the catch
     * here ever runs. Wrapping this method would turn that caught, expected exception
     * into an {@code UnexpectedRollbackException} on commit. Each repository call below
     * already runs in its own implicit transaction (Spring Data's default) — which is
     * exactly why every one of them takes an explicit company id rather than trusting a
     * per-session Hibernate filter to have been enabled on whichever short-lived session
     * that particular call happens to run on (ISSUE-001).
     */
    @Override
    public DashboardSummaryResponse summary() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        boolean crossTenant = caller.isSuperAdmin();
        // A SUPER_ADMIN's own JWT still carries a (sentinel) company id — CompanyContext
        // is never actually empty for that role — so scope is null (genuinely
        // cross-tenant) only via the explicit isSuperAdmin() check, never inferred from
        // "no company bound".
        UUID scope = crossTenant ? null : CompanyContext.requireCompanyId();

        LocalDate today = LocalDate.now();
        // "Today's Shipments" / Delivered / In Transit / Pending / Collection are all
        // month-to-date, not literally today — first-of-month through today, inclusive.
        LocalDate monthStart = today.withDayOfMonth(1);
        long todayShipments;
        long delivered;
        long inTransit;
        long pending;
        long totalShipments;
        BigDecimal totalRevenue;
        BigDecimal todayCollection;
        List<Shipment> recent;

        if (crossTenant) {
            // runAs(null, ...) is this platform's own sanctioned way to disable the
            // Hibernate companyFilter for a deliberately cross-company read — the same
            // pattern TicketServiceImpl.dashboard uses for the identical SUPER_ADMIN case.
            todayShipments = CompanyContext.runAs(null,
                    () -> shipmentRepository.countByBookingDateBetween(monthStart, today));
            delivered = CompanyContext.runAs(null, () -> shipmentRepository.countByStatusAndBookingDateBetween(
                    ShipmentStatus.DELIVERED, monthStart, today));
            inTransit = CompanyContext.runAs(null,
                    () -> shipmentRepository.countByStatusInAndBookingDateBetween(IN_TRANSIT, monthStart, today));
            pending = CompanyContext.runAs(null,
                    () -> shipmentRepository.countByStatusInAndBookingDateBetween(PENDING, monthStart, today));
            totalShipments = CompanyContext.<Long>runAs(null, () -> shipmentRepository.count());
            totalRevenue = CompanyContext.<BigDecimal>runAs(null, () -> shipmentChargeRepository.sumNetAmount());
            todayCollection = CompanyContext.<BigDecimal>runAs(null,
                    () -> shipmentChargeRepository.sumNetAmountForBookingDateBetween(monthStart, today));
            recent = CompanyContext.<List<Shipment>>runAs(null,
                    () -> shipmentRepository.findTop5ByOrderByCreatedAtDesc());
        } else {
            todayShipments = shipmentRepository.countByCompanyIdAndBookingDateBetween(scope, monthStart, today);
            delivered = shipmentRepository.countByCompanyIdAndStatusAndBookingDateBetween(
                    scope, ShipmentStatus.DELIVERED, monthStart, today);
            inTransit = shipmentRepository.countByCompanyIdAndStatusInAndBookingDateBetween(
                    scope, IN_TRANSIT, monthStart, today);
            pending = shipmentRepository.countByCompanyIdAndStatusInAndBookingDateBetween(
                    scope, PENDING, monthStart, today);
            totalShipments = shipmentRepository.countByCompanyId(scope);
            totalRevenue = shipmentChargeRepository.sumNetAmountByCompanyId(scope);
            todayCollection = shipmentChargeRepository.sumNetAmountByCompanyIdAndBookingDateBetween(
                    scope, monthStart, today);
            recent = shipmentRepository.findTop5ByCompanyIdOrderByCreatedAtDesc(scope);
        }

        Wallet ownWallet = ownWallet();
        BigDecimal walletBalance = ownWallet == null ? null : ownWallet.getAvailableBalance();
        // No own branch (company/platform admins) means no "Pending Delivery" tile is
        // shown for them either — 0 is a safe, unused default, not a fabricated figure.
        // A caller with an own branch is by construction never the cross-tenant
        // SUPER_ADMIN case (that role has no branch), so `scope` is always their real
        // company here.
        long pendingDelivery = ownWallet == null ? 0L
                : shipmentRepository.countByCompanyIdAndDeliveryBranchIdAndStatusIn(
                        scope, ownWallet.getBranchId(), PENDING_DELIVERY);

        DashboardStatisticsResponse statistics = new DashboardStatisticsResponse(
                todayShipments, delivered, inTransit, pending, totalRevenue,
                todayShipments, todayCollection, pendingDelivery, totalShipments, walletBalance);

        return new DashboardSummaryResponse(statistics, recentShipments(recent));
    }

    /**
     * The caller's own branch wallet, or null for a caller with no own branch
     * (company/platform admins) — {@code WalletService.getForBranch(null)} throws
     * {@link BusinessRuleException} for them, which the dashboard degrades rather than
     * surfaces, the same convention as every other omitted figure in this response.
     */
    private Wallet ownWallet() {
        try {
            return walletService.getForBranch(null);
        } catch (BusinessRuleException e) {
            return null;
        }
    }

    private List<RecentShipmentResponse> recentShipments(List<Shipment> recent) {
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
