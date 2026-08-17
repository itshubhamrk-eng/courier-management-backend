package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    @Query("select m from TicketMessage m where m.ticketId = :ticketId and m.companyId = :companyId "
            + "order by m.createdAt asc")
    List<TicketMessage> findByTicket(@Param("ticketId") UUID ticketId, @Param("companyId") UUID companyId);
}
