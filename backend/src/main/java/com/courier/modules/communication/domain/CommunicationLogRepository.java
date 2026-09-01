package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationLogRepository
        extends JpaRepository<CommunicationLog, UUID>, JpaSpecificationExecutor<CommunicationLog> {

    @Query("select l from CommunicationLog l where l.id = :id and l.companyId = :companyId")
    Optional<CommunicationLog> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<CommunicationLog> findByShipmentIdAndEventTypeAndChannel(
            UUID shipmentId, CommunicationEventType eventType, CommunicationChannel channel);

    List<CommunicationLog> findAllByShipmentIdAndCompanyIdOrderByEventTypeAscChannelAsc(
            UUID shipmentId, UUID companyId);

    /**
     * Cross-tenant on purpose — {@code CommunicationDispatchJob} runs on a scheduler thread
     * with no {@code CompanyContext} bound, the same discipline {@code TicketSlaSweepJob}/
     * {@code ShipmentSlaSweepJob}/{@code FollowUpSweepJob} already established: with no
     * company id bound, the Hibernate company filter is simply never enabled for this
     * query, so it genuinely sees every company's due rows in one sweep.
     */
    @Query("select l from CommunicationLog l where l.status = 'PENDING' "
            + "or (l.status = 'FAILED' and l.attemptCount < :maxAttempts and l.nextRetryAt <= :now)")
    List<CommunicationLog> findDueForDispatch(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    @Query("select l.channel as channel, l.status as status, count(l) as total from CommunicationLog l "
            + "where l.companyId = :companyId and l.createdAt >= :since group by l.channel, l.status")
    List<ChannelStatusCount> countTodayByChannelAndStatus(
            @Param("companyId") UUID companyId, @Param("since") Instant since);

    interface ChannelStatusCount {
        CommunicationChannel getChannel();
        CommunicationStatus getStatus();
        long getTotal();
    }
}
