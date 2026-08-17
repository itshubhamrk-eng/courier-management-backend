package com.courier.modules.support.application;

import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.support.application.command.CreateTicketCommand;
import com.courier.modules.support.application.command.UploadTicketAttachmentCommand;
import com.courier.modules.support.application.command.AssignmentCommand;
import com.courier.modules.support.domain.CompanyTicketSequenceRepository;
import com.courier.modules.support.domain.NotificationType;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketAssignmentHistoryRepository;
import com.courier.modules.support.domain.TicketAttachmentRepository;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketDirectoryPort;
import com.courier.modules.support.domain.TicketMessage;
import com.courier.modules.support.domain.TicketMessageRepository;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketRepository;
import com.courier.modules.support.domain.TicketSlaRule;
import com.courier.modules.support.domain.TicketStatus;
import com.courier.modules.support.domain.TicketStatusHistoryRepository;
import com.courier.modules.support.domain.TicketSubCategoryRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final UUID AGENT = UUID.randomUUID();

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketMessageRepository messageRepository;
    @Mock private TicketAttachmentRepository attachmentRepository;
    @Mock private TicketStatusHistoryRepository statusHistoryRepository;
    @Mock private TicketAssignmentHistoryRepository assignmentHistoryRepository;
    @Mock private TicketCategoryRepository categoryRepository;
    @Mock private TicketSubCategoryRepository subCategoryRepository;
    @Mock private CompanyTicketSequenceRepository sequenceRepository;
    @Mock private TicketDirectoryPort directory;
    @Mock private FileStoragePort fileStoragePort;
    @Mock private AuditService auditService;
    @Mock private com.courier.modules.support.domain.TicketSlaRuleRepository slaRuleRepository;
    @Mock private NotificationService notificationService;

    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketServiceImpl(ticketRepository, messageRepository, attachmentRepository,
                statusHistoryRepository, assignmentHistoryRepository, categoryRepository, subCategoryRepository,
                sequenceRepository, directory, fileStoragePort, auditService, slaRuleRepository, notificationService);

        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(categoryRepository.findById(CATEGORY)).thenReturn(Optional.of(
                TicketCategory.builder().name("Shipment Issue").active(true).build()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
        when(statusHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assignmentHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sequenceRepository.nextValue()).thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("ticket number is TKT- plus a zero-padded 6-digit serial")
    void ticketNumberFormat() {
        Ticket created = service.create(command());
        assertThat(created.getTicketNumber()).isEqualTo("TKT-000007");
        assertThat(created.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("a system-raised ticket needs no current user and stays OPEN with no assignee")
    void systemRaisedTicketNeedsNoCurrentUser() {
        SecurityContextHolder.clearContext();

        Ticket created = service.raiseSystemTicket(command(), null);

        assertThat(created.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(created.getCreatedByUserId()).isNull();
    }

    @Test
    @DisplayName("a system-raised ticket with a resolvable assignee goes straight to ASSIGNED")
    void systemRaisedTicketAutoAssigns() {
        SecurityContextHolder.clearContext();
        when(directory.findUser(AGENT, COMPANY)).thenReturn(
                Optional.of(new TicketDirectoryPort.UserRef(AGENT, COMPANY, "Branch Manager", "bm@test.local")));

        Ticket created = service.raiseSystemTicket(command(), AGENT);

        assertThat(created.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(created.getAssigneeUserId()).isEqualTo(AGENT);
        assertThat(created.getCreatedByUserId()).isNull();
    }

    @Test
    @DisplayName("a system-raised ticket with an unresolvable assignee falls back to unassigned")
    void systemRaisedTicketFallsBackWhenAssigneeUnknown() {
        SecurityContextHolder.clearContext();
        when(directory.findUser(AGENT, COMPANY)).thenReturn(Optional.empty());

        Ticket created = service.raiseSystemTicket(command(), AGENT);

        assertThat(created.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(created.getAssigneeUserId()).isNull();
    }

    @Test
    @DisplayName("a ticket outside the caller's company is invisible, not merely forbidden")
    void tenantIsolation() {
        UUID foreignId = UUID.randomUUID();
        when(ticketRepository.findByIdWithinCompany(foreignId, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(foreignId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an illegal status transition is refused")
    void illegalTransitionRejected() {
        UUID id = UUID.randomUUID();
        Ticket resolved = ticket(id, TicketStatus.RESOLVED, CALLER, AGENT);
        when(ticketRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(resolved));

        // RESOLVED -> OPEN is not a legal move.
        assertThatThrownBy(() -> service.changeStatus(id, TicketStatus.OPEN, "back to open"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("reopen is refused unless the ticket is RESOLVED or CLOSED")
    void reopenOnlyFromTerminal() {
        UUID id = UUID.randomUUID();
        Ticket open = ticket(id, TicketStatus.OPEN, CALLER, null);
        when(ticketRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(open));
        signedIn(Roles.COMPANY_ADMIN, CALLER); // the requester themself

        assertThatThrownBy(() -> service.reopen(id, "not happy"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("an internal note never reaches a caller who is neither staff nor the assignee")
    void internalNoteHiddenFromNonStaff() {
        UUID id = UUID.randomUUID();
        Ticket ticket = ticket(id, TicketStatus.IN_PROGRESS, CALLER, AGENT);
        when(ticketRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(ticket));

        TicketMessage publicReply = TicketMessage.builder().ticketId(id).authorUserId(AGENT)
                .body("We are looking into it").internalNote(false).build();
        TicketMessage internalNote = TicketMessage.builder().ticketId(id).authorUserId(AGENT)
                .body("Escalate to logistics").internalNote(true).build();
        when(messageRepository.findByTicket(id, COMPANY)).thenReturn(List.of(publicReply, internalNote));

        // The requester (CALLER) is not staff: not an admin and not this ticket's assignee.
        signedIn(Roles.BRANCH_MANAGER, CALLER);

        List<TicketMessage> visible = service.messages(id);

        assertThat(visible).containsExactly(publicReply);
    }

    @Test
    @DisplayName("an unsupported attachment extension is rejected before any upload is attempted")
    void attachmentExtensionRejected() {
        UUID id = UUID.randomUUID();
        Ticket ticket = ticket(id, TicketStatus.OPEN, CALLER, null);
        when(ticketRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(ticket));

        UploadTicketAttachmentCommand command = new UploadTicketAttachmentCommand(
                new byte[]{1, 2, 3}, "payload.exe", "application/octet-stream", null);

        assertThatThrownBy(() -> service.uploadAttachment(id, command))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("an active SLA rule for the ticket's priority sets both due dates on create")
    void slaDueDatesComputedFromActiveRule() {
        when(slaRuleRepository.findByCompanyIdAndPriorityAndActiveTrue(COMPANY, TicketPriority.HIGH))
                .thenReturn(Optional.of(TicketSlaRule.builder()
                        .priority(TicketPriority.HIGH).firstResponseMinutes(30).resolutionMinutes(240).active(true).build()));

        Ticket created = service.create(command());

        assertThat(created.getSlaFirstResponseDueAt()).isNotNull();
        assertThat(created.getSlaResolutionDueAt()).isNotNull();
        assertThat(created.getSlaResolutionDueAt()).isAfter(created.getSlaFirstResponseDueAt());
    }

    @Test
    @DisplayName("no matching SLA rule leaves both due dates null, not a guessed default")
    void noSlaRuleLeavesDueDatesNull() {
        when(slaRuleRepository.findByCompanyIdAndPriorityAndActiveTrue(COMPANY, TicketPriority.HIGH))
                .thenReturn(Optional.empty());

        Ticket created = service.create(command());

        assertThat(created.getSlaFirstResponseDueAt()).isNull();
        assertThat(created.getSlaResolutionDueAt()).isNull();
    }

    @Test
    @DisplayName("assigning a ticket notifies the new assignee")
    void assignNotifiesNewAssignee() {
        UUID id = UUID.randomUUID();
        Ticket open = ticket(id, TicketStatus.OPEN, CALLER, null);
        when(ticketRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(open));
        when(directory.findUser(AGENT, COMPANY)).thenReturn(Optional.of(
                new TicketDirectoryPort.UserRef(AGENT, COMPANY, "Agent Name", "agent@test.local")));

        service.assign(id, new AssignmentCommand(AGENT, "taking this"));

        verify(notificationService).notify(eq(AGENT), eq(NotificationType.TICKET_ASSIGNED), any(), any(), eq(id));
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn(String role) {
        signedIn(role, CALLER);
    }

    private void signedIn(String role, UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, COMPANY, "user@test.local", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Ticket ticket(UUID id, TicketStatus status, UUID requester, UUID assignee) {
        Ticket t = Ticket.builder()
                .ticketNumber("TKT-000001")
                .subject("Delivery delayed")
                .description("Package has not moved in 3 days")
                .categoryId(CATEGORY)
                .priority(TicketPriority.HIGH)
                .status(status)
                .createdByUserId(requester)
                .assigneeUserId(assignee)
                .escalated(false)
                .build();
        t.setId(id);
        t.setCompanyId(COMPANY);
        return t;
    }

    private static CreateTicketCommand command() {
        return new CreateTicketCommand("Delivery delayed", "Package has not moved in 3 days",
                CATEGORY, null, TicketPriority.HIGH, null, null, null, null);
    }
}
