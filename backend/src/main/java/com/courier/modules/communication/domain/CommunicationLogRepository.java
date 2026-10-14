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

    /** Cross-tenant on purpose: a delivery receipt arrives from Meta with nothing but the message id
     *  (no company, no bearer token), so — like {@link #findDueForDispatch} — this runs with no
     *  CompanyContext bound and the company filter is simply not applied. A WhatsApp message id is
     *  globally unique. */
    Optional<CommunicationLog> findFirstByProviderMessageId(String providerMessageId);

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

    /** The dashboard's counts for any window {@code [from, to)} — {@link #countTodayByChannelAndStatus}
     *  only knew "since", which cannot express "yesterday". */
    @Query("select l.channel as channel, l.status as status, count(l) as total from CommunicationLog l "
            + "where l.companyId = :companyId and l.createdAt >= :from and l.createdAt < :to "
            + "group by l.channel, l.status")
    List<ChannelStatusCount> countByChannelAndStatusBetween(
            @Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    /** Messages created in the window that the recipient has since opened. */
    long countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndReadAtNotNull(
            UUID companyId, Instant from, Instant to);

    /** (created, status) of every message that actually left in the window — bucketed per day in Java,
     *  in the reporting zone, because grouping by a zoned date in JPQL is not portable. */
    @Query("select l.createdAt as at, l.status as status from CommunicationLog l "
            + "where l.companyId = :companyId and l.createdAt >= :from and l.createdAt < :to "
            + "and l.status in (com.courier.modules.communication.domain.CommunicationStatus.SENT, "
            + "com.courier.modules.communication.domain.CommunicationStatus.DELIVERED, "
            + "com.courier.modules.communication.domain.CommunicationStatus.FAILED)")
    List<SeriesRow> findSeriesRows(@Param("companyId") UUID companyId, @Param("from") Instant from,
                                   @Param("to") Instant to);

    List<CommunicationLog> findTop8ByCompanyIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByUpdatedAtDesc(
            UUID companyId, CommunicationStatus status, Instant from, Instant to);

    /** The newest delivery receipt we have ever recorded — proof the webhook is wired up. */
    @Query("select max(l.deliveredAt) from CommunicationLog l where l.companyId = :companyId")
    Instant findLastDeliveredAt(@Param("companyId") UUID companyId);

    /** WhatsApp messages in the window, grouped by the category their template is filed under at Meta —
     *  what the cost estimate multiplies by a rate. Category is the template's current one (an
     *  estimate: re-filing a template later re-prices its past messages). */
    @Query("select t.category as category, count(l) as total from CommunicationLog l, CommunicationTemplate t "
            + "where l.templateId = t.id and l.companyId = :companyId "
            + "and l.channel = com.courier.modules.communication.domain.CommunicationChannel.WHATSAPP "
            + "and l.status in :statuses and l.createdAt >= :from and l.createdAt < :to group by t.category")
    List<CategoryCount> countWhatsAppByCategory(@Param("companyId") UUID companyId,
                                                @Param("statuses") java.util.Collection<CommunicationStatus> statuses,
                                                @Param("from") Instant from, @Param("to") Instant to);

    interface CategoryCount {
        String getCategory();
        long getTotal();
    }

    interface SeriesRow {
        Instant getAt();
        CommunicationStatus getStatus();
    }

    interface ChannelStatusCount {
        CommunicationChannel getChannel();
        CommunicationStatus getStatus();
        long getTotal();
    }
}
