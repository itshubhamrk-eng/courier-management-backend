package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    @Query("select a from TicketAttachment a where a.ticketId = :ticketId and a.companyId = :companyId "
            + "order by a.createdAt asc")
    List<TicketAttachment> findByTicket(@Param("ticketId") UUID ticketId, @Param("companyId") UUID companyId);
}
