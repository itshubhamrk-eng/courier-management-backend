package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.pdp.api.PdpDashboardDtos.DashboardResponse;
import com.courier.modules.pdp.api.PdpDashboardDtos.DeliveryFigures;
import com.courier.modules.pdp.api.PdpDashboardDtos.EarningFigures;
import com.courier.modules.pdp.api.PdpDashboardDtos.PartnerRow;
import com.courier.modules.pdp.api.PdpDashboardDtos.PickupFigures;
import com.courier.modules.pdp.api.PdpDashboardDtos.StatementFigures;
import com.courier.modules.pdp.api.PdpDashboardDtos.WaitingPickup;
import com.courier.modules.pdp.domain.PdpEarningRepository;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import com.courier.modules.pdp.domain.PdpStatementRepository;
import com.courier.modules.pdp.domain.PdpStatementStatus;
import com.courier.modules.pickup.domain.PickupRequest;
import com.courier.modules.pickup.domain.PickupRequestRepository;
import com.courier.modules.pickup.domain.PickupStatus;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates over pickups, shipments, earnings and statements. A company admin sees every partner (or one, via
 * {@code branchId}); a branch-bound caller is pinned to their own branch whatever they pass.
 */
@Service
@RequiredArgsConstructor
public class PdpDashboardServiceImpl implements PdpDashboardService {

    private static final int WAITING_LIMIT = 8;
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final EnumSet<ShipmentStatus> DELIVERY_STATUSES =
            EnumSet.of(ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED);

    private final PickupRequestRepository pickups;
    private final PdpEarningRepository earnings;
    private final PdpStatementRepository statements;
    private final ShipmentRepository shipments;
    private final BranchService branchService;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public DashboardResponse dashboard(UUID requested) {
        UUID company = CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. The dashboard belongs to a company."));
        UUID own = ownPartnerScope();
        UUID scope = own != null ? own : requested;

        Map<UUID, Branch> partners = branchService.listActiveDirectory().stream()
                .filter(b -> b.getBranchType() == BranchType.PICKUP_DELIVERY_PARTNER)
                .collect(Collectors.toMap(Branch::getId, b -> b, (a, b) -> a));
        if (scope != null && !partners.containsKey(scope)) {
            throw new BusinessRuleException("That branch is not an active Pickup-Delivery Partner.");
        }

        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay().toInstant(ZoneOffset.UTC);

        // pickups
        Map<UUID, Long> pickedUp = new HashMap<>();
        pickups.countPickedUpSince(company, monthStartInstant).forEach(r -> pickedUp.put((UUID) r[0], (Long) r[1]));
        Map<PickupStatus, Long> byStatus = new EnumMap<>(PickupStatus.class);
        for (PickupStatus s : PickupStatus.values()) {
            byStatus.put(s, 0L);
        }
        Map<UUID, Long> openByPartner = new HashMap<>();
        for (Object[] row : pickups.countByStatus(company, scope)) {
            byStatus.merge((PickupStatus) row[0], (Long) row[1], Long::sum);
        }
        if (scope == null) {
            for (UUID id : partners.keySet()) {
                openByPartner.put(id, openCount(pickups.countByStatus(company, id)));
            }
        }
        long open = byStatus.entrySet().stream().filter(e -> e.getKey().isOpen()).mapToLong(Map.Entry::getValue).sum();
        long unaccepted = byStatus.get(PickupStatus.REQUESTED) + byStatus.get(PickupStatus.ASSIGNED);
        long pickedUpMonth = pickedUp.entrySet().stream().filter(e -> scope == null || scope.equals(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();

        // deliveries
        Map<UUID, Map<ShipmentStatus, Long>> ship = new HashMap<>();
        for (Object[] r : shipments.countByDeliveryBranchAndStatus(company, DELIVERY_STATUSES)) {
            ship.computeIfAbsent((UUID) r[0], k -> new EnumMap<>(ShipmentStatus.class)).put((ShipmentStatus) r[1], (Long) r[2]);
        }

        // earnings: all time (for pending/unrated/accrued) + this month
        List<Object[]> allTime = earnings.summarise(company, scope, EPOCH);
        List<Object[]> month = earnings.summarise(company, scope, monthStart);
        Map<UUID, Long> approvedDeliveries = new HashMap<>();
        Map<UUID, BigDecimal> accruedBy = new HashMap<>();
        Map<UUID, Long> deliveredMonth = new HashMap<>();
        BigDecimal accrued = BigDecimal.ZERO;
        long accruedCount = 0;
        long unrated = 0;
        for (Object[] r : allTime) {
            UUID b = (UUID) r[0];
            PdpEarningType type = (PdpEarningType) r[1];
            PdpEarningStatus st = (PdpEarningStatus) r[2];
            long n = (Long) r[3];
            BigDecimal amt = (BigDecimal) r[4];
            if (type == PdpEarningType.DELIVERY && st != PdpEarningStatus.REVERSED) {
                approvedDeliveries.merge(b, n, Long::sum);
            }
            if (st == PdpEarningStatus.ACCRUED) {
                accrued = accrued.add(amt);
                accruedCount += n;
                accruedBy.merge(b, amt, BigDecimal::add);
            } else if (st == PdpEarningStatus.UNRATED) {
                unrated += n;
            }
        }
        BigDecimal billedMonth = BigDecimal.ZERO;
        BigDecimal earnedMonth = BigDecimal.ZERO;
        for (Object[] r : month) {
            PdpEarningStatus st = (PdpEarningStatus) r[2];
            if (st == PdpEarningStatus.REVERSED) {
                continue;
            }
            BigDecimal amt = (BigDecimal) r[4];
            earnedMonth = earnedMonth.add(amt);
            if (st == PdpEarningStatus.BILLED) {
                billedMonth = billedMonth.add(amt);
            }
            if ((PdpEarningType) r[1] == PdpEarningType.DELIVERY) {
                deliveredMonth.merge((UUID) r[0], (Long) r[3], Long::sum);
            }
        }

        // statements
        Map<PdpStatementStatus, Long> stCount = new EnumMap<>(PdpStatementStatus.class);
        Map<UUID, BigDecimal> payableBy = new HashMap<>();
        BigDecimal outstanding = BigDecimal.ZERO;
        for (Object[] r : statements.summarise(company, scope)) {
            PdpStatementStatus st = (PdpStatementStatus) r[1];
            stCount.merge(st, (Long) r[2], Long::sum);
            if (st != PdpStatementStatus.PAID) {
                BigDecimal amt = (BigDecimal) r[3];
                outstanding = outstanding.add(amt);
                payableBy.merge((UUID) r[0], amt, BigDecimal::add);
            }
        }
        BigDecimal paidMonth = statements.paidSince(company, scope, monthStart);

        // deliveries (cont.) — totals over the scope
        long atPartner = 0;
        long outForDelivery = 0;
        long delivered = 0;
        for (Map.Entry<UUID, Map<ShipmentStatus, Long>> e : ship.entrySet()) {
            if (!partners.containsKey(e.getKey()) || (scope != null && !scope.equals(e.getKey()))) {
                continue;
            }
            atPartner += e.getValue().getOrDefault(ShipmentStatus.IN_SCAN, 0L);
            outForDelivery += e.getValue().getOrDefault(ShipmentStatus.OUT_FOR_DELIVERY, 0L);
            delivered += e.getValue().getOrDefault(ShipmentStatus.DELIVERED, 0L);
        }
        long approved = approvedDeliveries.entrySet().stream().filter(e -> scope == null || scope.equals(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        long deliveredMonthTotal = deliveredMonth.values().stream().mapToLong(Long::longValue).sum();

        // pickups waiting for a partner to accept them, oldest first
        List<WaitingPickup> waiting = pickups.findOpenByStatus(company,
                        EnumSet.of(PickupStatus.REQUESTED, PickupStatus.ASSIGNED), scope, PageRequest.of(0, WAITING_LIMIT))
                .stream().map(p -> toWaiting(p, partners)).toList();

        List<PartnerRow> rows = scope != null ? List.of() : partners.values().stream()
                .map(b -> {
                    Map<ShipmentStatus, Long> sh = ship.getOrDefault(b.getId(), Map.of());
                    return new PartnerRow(b.getId(), b.getBranchCode(), b.getBranchName(),
                            openByPartner.getOrDefault(b.getId(), 0L), pickedUp.getOrDefault(b.getId(), 0L),
                            sh.getOrDefault(ShipmentStatus.IN_SCAN, 0L) + sh.getOrDefault(ShipmentStatus.OUT_FOR_DELIVERY, 0L),
                            deliveredMonth.getOrDefault(b.getId(), 0L),
                            accruedBy.getOrDefault(b.getId(), BigDecimal.ZERO), payableBy.getOrDefault(b.getId(), BigDecimal.ZERO));
                })
                .sorted(Comparator.comparing(PartnerRow::accruedAmount).reversed().thenComparing(PartnerRow::branchName))
                .toList();

        Branch focus = scope == null ? null : partners.get(scope);
        return new DashboardResponse(scope, focus == null ? null : focus.getBranchName(),
                new PickupFigures(byStatus, open, unaccepted, pickedUpMonth),
                new DeliveryFigures(atPartner, outForDelivery, delivered, Math.max(delivered - approved, 0), deliveredMonthTotal),
                new EarningFigures(accrued, accruedCount, unrated, billedMonth, earnedMonth),
                new StatementFigures(stCount.getOrDefault(PdpStatementStatus.DRAFT, 0L),
                        stCount.getOrDefault(PdpStatementStatus.APPROVED, 0L), stCount.getOrDefault(PdpStatementStatus.PAID, 0L),
                        outstanding, paidMonth),
                waiting, rows);
    }

    private static long openCount(List<Object[]> byStatusRows) {
        long n = 0;
        for (Object[] r : byStatusRows) {
            if (((PickupStatus) r[0]).isOpen()) {
                n += (Long) r[1];
            }
        }
        return n;
    }

    private static WaitingPickup toWaiting(PickupRequest p, Map<UUID, Branch> partners) {
        Branch b = p.getPdpBranchId() == null ? null : partners.get(p.getPdpBranchId());
        return new WaitingPickup(p.getId(), p.getPickupNo(), p.getStatus().name(), p.getContactName(), p.getPincode(), p.getCity(),
                p.getPdpBranchId(), b == null ? null : b.getBranchName(), p.getCreatedAt());
    }

    /** The caller's own branch when branch-bound (a partner), else null. Same rule the earnings and pickup services use. */
    private UUID ownPartnerScope() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (caller.isPlatformTier() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return null;
        }
        UUID branchId = userService.getCurrent().getBranchId();
        return branchId != null ? branchId : new UUID(0L, 0L);
    }
}
