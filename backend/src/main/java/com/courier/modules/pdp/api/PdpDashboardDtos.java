package com.courier.modules.pdp.api;

import com.courier.modules.pickup.domain.PickupStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract of {@code GET /api/v1/pdp-dashboard}. */
public final class PdpDashboardDtos {

    private PdpDashboardDtos() {
    }

    public record PickupFigures(Map<PickupStatus, Long> byStatus, long open, long unaccepted, long pickedUpThisMonth) {
    }

    /** {@code podPending} = delivered by the partner but no delivery earning yet (POD not approved). */
    public record DeliveryFigures(long atPartner, long outForDelivery, long delivered, long podPending, long deliveredThisMonth) {
    }

    public record EarningFigures(BigDecimal accruedAmount, long accruedCount, long unratedCount, BigDecimal billedThisMonth,
                                 BigDecimal earnedThisMonth) {
    }

    public record StatementFigures(long draft, long approved, long paid, BigDecimal outstandingPayable, BigDecimal paidThisMonth) {
    }

    public record WaitingPickup(UUID id, String pickupNo, String status, String contactName, String pincode, String city,
                                UUID pdpBranchId, String pdpBranchName, Instant createdAt) {
    }

    /** One row per active partner (company view only). */
    public record PartnerRow(UUID branchId, String branchCode, String branchName, long openPickups, long pickedUpThisMonth,
                             long atPartner, long deliveredThisMonth, BigDecimal accruedAmount, BigDecimal outstandingPayable) {
    }

    public record DashboardResponse(UUID branchId, String branchName, PickupFigures pickups, DeliveryFigures deliveries,
                                    EarningFigures earnings, StatementFigures statements, List<WaitingPickup> waitingPickups,
                                    List<PartnerRow> partners) {
    }
}
