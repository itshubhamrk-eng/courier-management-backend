package com.courier.modules.support.application;

import com.courier.modules.support.application.command.AssignmentCommand;
import com.courier.modules.support.application.command.CreateTicketCommand;
import com.courier.modules.support.application.command.ReplyCommand;
import com.courier.modules.support.application.command.UploadTicketAttachmentCommand;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketAssignmentHistory;
import com.courier.modules.support.domain.TicketAttachment;
import com.courier.modules.support.domain.TicketCriteria;
import com.courier.modules.support.domain.TicketMessage;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketStatus;
import com.courier.modules.support.domain.TicketStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * A support ticket, from creation through the full lifecycle to closure and — if the
 * requester is unsatisfied — reopening. See {@code MEMORY} for the Phase 1 scope: SLA
 * rules and notifications are Phase 2, deliberately not here.
 */
public interface TicketService {

    /** Any authenticated company user; SUPER_ADMIN must name a target company. */
    Ticket create(CreateTicketCommand command);

    /** Raised by system automation (e.g. {@code ShipmentSlaSweepJob}), not a human caller
     *  — no current-user requirement, {@code createdByUserId} stays null. The caller must
     *  already have bound {@code CompanyContext} to the target company. If
     *  {@code assigneeUserId} is given and resolves to a real company user, the ticket is
     *  raised straight into {@code ASSIGNED}; otherwise it stays {@code OPEN}. */
    Ticket raiseSystemTicket(CreateTicketCommand command, UUID assigneeUserId);

    /** One ticket, within the caller's scope. Foreign or out-of-scope answers 404. */
    Ticket getById(UUID id);

    /** Paged, filtered. A non-admin caller's results are pinned to requester/assignee/own branch. */
    Page<Ticket> search(TicketCriteria criteria, Pageable pageable);

    /** The conversation thread. Internal notes are stripped for a caller who is not staff. */
    List<TicketMessage> messages(UUID ticketId);

    List<TicketAttachment> attachments(UUID ticketId);

    List<TicketStatusHistory> statusHistory(UUID ticketId);

    List<TicketAssignmentHistory> assignmentHistory(UUID ticketId);

    /** Requester or staff on this ticket. Sets {@code firstResponseAt} on a staff caller's
     *  first public reply. */
    TicketMessage reply(UUID ticketId, ReplyCommand command);

    TicketAttachment uploadAttachment(UUID ticketId, UploadTicketAttachmentCommand command);

    /** COMPANY_ADMIN/SUPER_ADMIN. Only from OPEN — use {@link #reassign} afterwards. */
    Ticket assign(UUID ticketId, AssignmentCommand command);

    /** COMPANY_ADMIN/SUPER_ADMIN. Any non-terminal status. */
    Ticket reassign(UUID ticketId, AssignmentCommand command);

    /** COMPANY_ADMIN/SUPER_ADMIN. Marks the ticket escalated; optionally reassigns too. */
    Ticket escalate(UUID ticketId, AssignmentCommand command);

    /** The assignee or COMPANY_ADMIN/SUPER_ADMIN. Refused if not a legal transition. */
    Ticket changeStatus(UUID ticketId, TicketStatus status, String remarks);

    Ticket changePriority(UUID ticketId, TicketPriority priority, String remarks);

    Ticket changeCategory(UUID ticketId, UUID categoryId, UUID subCategoryId, String remarks);

    /** The requester, or COMPANY_ADMIN/SUPER_ADMIN. Only from RESOLVED/CLOSED. */
    Ticket reopen(UUID ticketId, String remarks);

    /** The assignee or COMPANY_ADMIN/SUPER_ADMIN. Only from RESOLVED. */
    Ticket close(UUID ticketId, String remarks);

    /** SUPER_ADMIN: cross-tenant when {@code companyId} is null. Everyone else: own company. */
    TicketDashboardStats dashboard(UUID companyId);
}
