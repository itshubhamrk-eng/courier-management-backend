package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Show today's statistics" — {@code CommunicationLog.createdAt >= start of today, UTC}.
 * {@code Sent} folds in {@code DELIVERED} (a delivered message was necessarily sent first;
 * see {@code CommunicationStatus}'s own doc for why {@code DELIVERED} itself stays at zero
 * in this dev environment — no provider delivery-receipt webhook exists yet for any
 * channel), matching the brief's own worked example where Delivered is always a subset of
 * Sent, never the other way round.
 */
@Service
@RequiredArgsConstructor
public class CommunicationDashboardServiceImpl implements CommunicationDashboardService {

    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";

    private final CommunicationLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public CommunicationDashboardSummary today() {
        UUID companyId = requireCompany();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<CommunicationChannel, ChannelSummary> channels = new EnumMap<>(CommunicationChannel.class);
        for (CommunicationChannel channel : CommunicationChannel.values()) {
            channels.put(channel, new ChannelSummary(0, 0, 0, 0, 0));
        }

        long totalSent = 0, totalDelivered = 0, totalFailed = 0, totalPending = 0;
        for (var row : repository.countTodayByChannelAndStatus(companyId, startOfToday)) {
            ChannelSummary current = channels.get(row.getChannel());
            long sent = current.sent(), delivered = current.delivered(), failed = current.failed(),
                    pending = current.pending(), cancelled = current.cancelled();
            long count = row.getTotal();
            switch (row.getStatus()) {
                case SENT -> {
                    sent += count;
                    totalSent += count;
                }
                case DELIVERED -> {
                    sent += count;
                    delivered += count;
                    totalSent += count;
                    totalDelivered += count;
                }
                case FAILED -> {
                    failed += count;
                    totalFailed += count;
                }
                case PENDING -> {
                    pending += count;
                    totalPending += count;
                }
                case CANCELLED -> cancelled += count;
                default -> {
                }
            }
            channels.put(row.getChannel(), new ChannelSummary(sent, delivered, failed, pending, cancelled));
        }

        return new CommunicationDashboardSummary(totalSent, totalDelivered, totalFailed, totalPending, channels);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. The communication dashboard belongs to a company."));
    }
}
