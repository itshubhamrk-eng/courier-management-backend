package com.courier.modules.support.application;

import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param averageResolutionHours null when nothing has ever been resolved
 * @param byTenant only populated for a SUPER_ADMIN's cross-tenant dashboard
 * @param slaBreached live count of open tickets past their resolution due date — 0 for
 *                    a SUPER_ADMIN's cross-tenant view (SLA rules are company-scoped,
 *                    a cross-tenant breach count would mix each company's own targets)
 * @param slaPerformance ON_TRACK / WARNING / BREACHED / NO_SLA counts across every
 *                       currently-open ticket; empty for cross-tenant, same reason
 */
public record TicketDashboardStats(
        long totalTickets,
        long openTickets,
        long assignedTickets,
        long inProgress,
        long waitingForUser,
        long waitingForInternalTeam,
        long criticalTickets,
        long resolvedToday,
        long closedToday,
        long slaBreached,
        Double averageResolutionHours,
        Map<TicketStatus, Long> byStatus,
        Map<TicketPriority, Long> byPriority,
        Map<UUID, Long> byCategory,
        Map<UUID, Long> byBranch,
        Map<UUID, Long> byAgent,
        Map<UUID, Long> byTenant,
        Map<String, Long> slaPerformance,
        List<DailyCount> volumeTrend
) {
    public record DailyCount(String date, long total) {
    }
}
