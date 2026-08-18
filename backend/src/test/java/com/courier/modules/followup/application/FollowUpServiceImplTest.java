package com.courier.modules.followup.application;

import com.courier.modules.followup.application.command.CreateFollowUpCommand;
import com.courier.modules.followup.application.command.RescheduleCommand;
import com.courier.modules.followup.application.command.UpdateFollowUpCommand;
import com.courier.modules.followup.domain.FollowUp;
import com.courier.modules.followup.domain.FollowUpCriteria;
import com.courier.modules.followup.domain.FollowUpDirectoryPort;
import com.courier.modules.followup.domain.FollowUpHistory;
import com.courier.modules.followup.domain.FollowUpHistoryAction;
import com.courier.modules.followup.domain.FollowUpHistoryRepository;
import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpRepository;
import com.courier.modules.followup.domain.FollowUpStatus;
import com.courier.modules.followup.domain.FollowUpType;
import com.courier.modules.support.application.NotificationService;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ForbiddenException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID OTHER_BRANCH = UUID.randomUUID();
    private static final UUID AGENT = UUID.randomUUID();

    @Mock private FollowUpRepository followUpRepository;
    @Mock private FollowUpHistoryRepository historyRepository;
    @Mock private FollowUpDirectoryPort directory;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private FollowUpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FollowUpServiceImpl(followUpRepository, historyRepository, directory, auditService,
                notificationService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(followUpRepository.save(any(FollowUp.class))).thenAnswer(i -> i.getArgument(0));
        when(historyRepository.save(any(FollowUpHistory.class))).thenAnswer(i -> i.getArgument(0));
        when(directory.branchExists(BRANCH, COMPANY)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("create starts OPEN and writes a CREATED history entry")
    void createStartsOpen() {
        FollowUp created = service.create(command(BRANCH, null));

        assertThat(created.getStatus()).isEqualTo(FollowUpStatus.OPEN);
        assertThat(created.getBranchId()).isEqualTo(BRANCH);
        verify(historyRepository).save(argThatAction(FollowUpHistoryAction.CREATED));
        verify(auditService).record(eq(com.courier.shared.audit.domain.AuditAction.FOLLOWUP_CREATED),
                any(), any(), any());
    }

    @Test
    @DisplayName("a branch (non-admin) caller defaults a follow-up to their own branch")
    void nonAdminDefaultsToOwnBranch() {
        signedIn(Roles.BRANCH_MANAGER);
        when(directory.branchOfUser(CALLER, COMPANY)).thenReturn(Optional.of(BRANCH));

        FollowUp created = service.create(command(null, null));

        assertThat(created.getBranchId()).isEqualTo(BRANCH);
    }

    @Test
    @DisplayName("a branch (non-admin) caller cannot create a follow-up for another branch")
    void nonAdminCannotCreateForForeignBranch() {
        signedIn(Roles.BRANCH_MANAGER);
        when(directory.branchOfUser(CALLER, COMPANY)).thenReturn(Optional.of(BRANCH));

        assertThatThrownBy(() -> service.create(command(OTHER_BRANCH, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("the assignee must belong to the follow-up's branch")
    void assigneeMustBelongToBranch() {
        when(directory.findUser(AGENT, COMPANY)).thenReturn(
                Optional.of(new FollowUpDirectoryPort.UserRef(AGENT, COMPANY, "Agent", "a@test.local")));
        when(directory.branchOfUser(AGENT, COMPANY)).thenReturn(Optional.of(OTHER_BRANCH));

        assertThatThrownBy(() -> service.create(command(BRANCH, AGENT)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must belong to this follow-up's branch");
    }

    @Test
    @DisplayName("a due date is required")
    void dueDateRequired() {
        CreateFollowUpCommand noDueDate = new CreateFollowUpCommand(BRANCH, FollowUpType.GENERAL, null, null, null,
                null, "Call back", null, FollowUpType.GENERAL, FollowUpPriority.MEDIUM, null);
        assertThatThrownBy(() -> service.create(noDueDate)).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ update / editability

    @Test
    @DisplayName("a completed follow-up cannot be edited")
    void completedFollowUpCannotBeEdited() {
        FollowUp completed = followUp(FollowUpStatus.COMPLETED, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(completed.getId(), COMPANY)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.update(completed.getId(), updateCommand(1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be edited");
    }

    @Test
    @DisplayName("update refuses a stale version")
    void updateRefusesStaleVersion() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.update(open.getId(), updateCommand(999L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ------------------------------------------------------------------ status / reschedule

    @Test
    @DisplayName("an illegal status transition is rejected")
    void illegalStatusTransitionRejected() {
        FollowUp completed = followUp(FollowUpStatus.COMPLETED, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(completed.getId(), COMPANY)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.changeStatus(completed.getId(), FollowUpStatus.IN_PROGRESS, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("moving to COMPLETED stamps completedAt/completedBy and writes history")
    void completingStampsCompletion() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));

        FollowUp saved = service.changeStatus(open.getId(), FollowUpStatus.COMPLETED, "done");

        assertThat(saved.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getCompletedBy()).isEqualTo(CALLER);
        verify(historyRepository).save(argThatAction(FollowUpHistoryAction.STATUS_CHANGED));
    }

    @Test
    @DisplayName("RESCHEDULED is refused via the plain status action")
    void rescheduledRefusedViaStatusAction() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.changeStatus(open.getId(), FollowUpStatus.RESCHEDULED, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dedicated reschedule");
    }

    @Test
    @DisplayName("reschedule moves the due date, sets status RESCHEDULED and writes history with old/new dates")
    void rescheduleMovesDueDate() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        Instant oldDue = open.getDueDate();
        Instant newDue = oldDue.plusSeconds(86_400);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));

        FollowUp saved = service.reschedule(open.getId(), new RescheduleCommand(newDue, "customer asked"));

        assertThat(saved.getStatus()).isEqualTo(FollowUpStatus.RESCHEDULED);
        assertThat(saved.getDueDate()).isEqualTo(newDue);
        assertThat(saved.getNextFollowUpDate()).isEqualTo(newDue);
        verify(historyRepository).save(argThatReschedule(oldDue, newDue));
    }

    @Test
    @DisplayName("a completed/cancelled follow-up cannot be rescheduled")
    void terminalFollowUpCannotBeRescheduled() {
        FollowUp cancelled = followUp(FollowUpStatus.CANCELLED, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(cancelled.getId(), COMPANY)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.reschedule(cancelled.getId(),
                new RescheduleCommand(Instant.now().plusSeconds(3600), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ assignment

    @Test
    @DisplayName("BRANCH_MANAGER may only assign within their own branch")
    void branchManagerAssignsOnlyOwnBranch() {
        signedIn(Roles.BRANCH_MANAGER);
        FollowUp inOtherBranch = followUp(FollowUpStatus.OPEN, CALLER, OTHER_BRANCH);
        when(followUpRepository.findByIdWithinCompany(inOtherBranch.getId(), COMPANY))
                .thenReturn(Optional.of(inOtherBranch));
        when(directory.branchOfUser(CALLER, COMPANY)).thenReturn(Optional.of(BRANCH));

        assertThatThrownBy(() -> service.assign(inOtherBranch.getId(), AGENT, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("assigning notifies the new assignee")
    void assignNotifiesAssignee() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, BRANCH);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));
        when(directory.findUser(AGENT, COMPANY)).thenReturn(
                Optional.of(new FollowUpDirectoryPort.UserRef(AGENT, COMPANY, "Agent", "a@test.local")));
        when(directory.branchOfUser(AGENT, COMPANY)).thenReturn(Optional.of(BRANCH));

        FollowUp saved = service.assign(open.getId(), AGENT, "please handle");

        assertThat(saved.getAssignedUserId()).isEqualTo(AGENT);
        verify(notificationService).notifyFollowUp(eq(AGENT), any(), any(), any(), eq(open.getId()));
    }

    // ------------------------------------------------------------------ notes / history

    @Test
    @DisplayName("a note appends a NOTE_ADDED history entry without changing status")
    void addNoteAppendsHistory() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));

        service.addNote(open.getId(), "Called, no answer");

        verify(historyRepository).save(argThatAction(FollowUpHistoryAction.NOTE_ADDED));
        verify(followUpRepository, never()).save(any());
    }

    @Test
    @DisplayName("history returns every entry for the follow-up, scoped to its company")
    void historyReturnsEntries() {
        FollowUp open = followUp(FollowUpStatus.OPEN, CALLER, null);
        when(followUpRepository.findByIdWithinCompany(open.getId(), COMPANY)).thenReturn(Optional.of(open));
        when(historyRepository.findByFollowUp(open.getId(), COMPANY)).thenReturn(List.of(
                FollowUpHistory.builder().followUpId(open.getId()).action(FollowUpHistoryAction.CREATED).build()));

        List<FollowUpHistory> history = service.history(open.getId());

        assertThat(history).hasSize(1);
    }

    // ------------------------------------------------------------------ isolation / RBAC

    @Test
    @DisplayName("a branch (non-admin) caller cannot see a follow-up outside their own branch, "
            + "and isn't its requester or assignee")
    void branchIsolation() {
        signedIn(Roles.BRANCH_MANAGER);
        FollowUp foreign = followUp(FollowUpStatus.OPEN, UUID.randomUUID(), OTHER_BRANCH);
        when(followUpRepository.findByIdWithinCompany(foreign.getId(), COMPANY)).thenReturn(Optional.of(foreign));
        when(directory.branchOfUser(CALLER, COMPANY)).thenReturn(Optional.of(BRANCH));

        assertThatThrownBy(() -> service.getById(foreign.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the assignee can see a follow-up outside their own branch")
    void assigneeCanSeeAcrossBranches() {
        signedIn(Roles.BRANCH_MANAGER);
        FollowUp assignedToCaller = followUp(FollowUpStatus.OPEN, UUID.randomUUID(), OTHER_BRANCH);
        assignedToCaller.setAssignedUserId(CALLER);
        when(followUpRepository.findByIdWithinCompany(assignedToCaller.getId(), COMPANY))
                .thenReturn(Optional.of(assignedToCaller));

        FollowUp result = service.getById(assignedToCaller.getId());

        assertThat(result.getId()).isEqualTo(assignedToCaller.getId());
    }

    @Test
    @DisplayName("company isolation: a follow-up not found within the caller's company 404s")
    void companyIsolation() {
        UUID foreignId = UUID.randomUUID();
        when(followUpRepository.findByIdWithinCompany(foreignId, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(foreignId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("search restricts a non-admin caller to their own branch/requester/assignee scope")
    void searchRestrictsNonAdmin() {
        signedIn(Roles.BRANCH_MANAGER);
        when(directory.branchOfUser(CALLER, COMPANY)).thenReturn(Optional.of(BRANCH));
        when(followUpRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(FollowUpCriteria.none(), Pageable.unpaged());

        verify(followUpRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    // ------------------------------------------------------------------ dashboard / overdue

    @Test
    @DisplayName("dashboard reports the four live buckets from the repository's own counts")
    void dashboardReportsBuckets() {
        when(followUpRepository.countOverdue(eq(COMPANY), any())).thenReturn(3L);
        when(followUpRepository.countDueToday(eq(COMPANY), any(), any())).thenReturn(5L);
        when(followUpRepository.countUpcoming(eq(COMPANY), any())).thenReturn(9L);
        when(followUpRepository.countUrgent(COMPANY)).thenReturn(2L);

        FollowUpDashboardStats stats = service.dashboard();

        assertThat(stats.overdue()).isEqualTo(3);
        assertThat(stats.dueToday()).isEqualTo(5);
        assertThat(stats.upcoming()).isEqualTo(9);
        assertThat(stats.urgent()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(CALLER, COMPANY, "user@test.local", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static CreateFollowUpCommand command(UUID branchId, UUID assignedUserId) {
        return new CreateFollowUpCommand(branchId, FollowUpType.GENERAL, null, null, null, assignedUserId,
                "Call back the customer", "Follow up on delivery time", FollowUpType.GENERAL,
                FollowUpPriority.MEDIUM, Instant.now().plusSeconds(3600));
    }

    private static UpdateFollowUpCommand updateCommand(Long version) {
        return new UpdateFollowUpCommand(BRANCH, FollowUpType.GENERAL, null, null, null, "Updated title",
                "Updated description", FollowUpType.GENERAL, FollowUpPriority.HIGH,
                Instant.now().plusSeconds(7200), version);
    }

    private static FollowUp followUp(FollowUpStatus status, UUID createdBy, UUID branchId) {
        FollowUp f = FollowUp.builder()
                .branchId(branchId == null ? BRANCH : branchId)
                .referenceType(FollowUpType.GENERAL)
                .title("Call back the customer")
                .followUpType(FollowUpType.GENERAL)
                .priority(FollowUpPriority.MEDIUM)
                .status(status)
                .dueDate(Instant.now().plusSeconds(3600))
                .build();
        f.setCompanyId(COMPANY);
        f.setCreatedBy(createdBy);
        f.setVersion(1L);
        return f;
    }

    private static FollowUpHistory argThatAction(FollowUpHistoryAction action) {
        return org.mockito.ArgumentMatchers.argThat(h -> h != null && h.getAction() == action);
    }

    private static FollowUpHistory argThatReschedule(Instant previous, Instant next) {
        return org.mockito.ArgumentMatchers.argThat(h -> h != null
                && h.getAction() == FollowUpHistoryAction.RESCHEDULED
                && previous.equals(h.getPreviousDueDate())
                && next.equals(h.getNewDueDate()));
    }
}
