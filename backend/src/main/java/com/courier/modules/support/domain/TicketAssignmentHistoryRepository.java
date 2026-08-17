package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketAssignmentHistoryRepository extends JpaRepository<TicketAssignmentHistory, UUID> {

    @Query("select h from TicketAssignmentHistory h where h.ticketId = :ticketId and h.companyId = :companyId "
            + "order by h.createdAt asc")
    List<TicketAssignmentHistory> findByTicket(@Param("ticketId") UUID ticketId, @Param("companyId") UUID companyId);
}
