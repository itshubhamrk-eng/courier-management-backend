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
import com.courier.modules.followup.domain.FollowUpSpecifications;
import com.courier.modules.followup.domain.FollowUpStatus;
import com.courier.modules.followup.domain.FollowUpType;
import com.courier.modules.support.application.NotificationService;
import com.courier.modules.support.domain.NotificationType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * See {@link FollowUpService}. Scoping is hand-rolled per method, mirroring {@code
 * TicketServiceImpl} — this codebase has no generic "requireVisible" helper. Unlike
 * Ticket Support, there is no SUPER_ADMIN cross-tenant view: a follow-up is purely a
 * company/branch operational record, so every method requires a bound company.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {

    private static final String ENTITY = "FollowUp";

    private static final String ASSIGN_ROLES =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.BRANCH_MANAGER + "', '" + Roles.HUB_MANAGER + "')";

    private final FollowUpRepository followUpRepository;
    private final FollowUpHistoryRepository historyRepository;
    private final FollowUpDirectoryPort directory;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ create

    @Override
    @Transactional
    public FollowUp create(CreateFollowUpCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = requireCompany();

        String title = requireText(command.title(), "Title");
        if (command.dueDate() == null) {
            throw new BusinessRuleException("A due date is required.");
        }
        UUID branchId = resolveBranchForWrite(caller, companyId, command.branchId());
        UUID assignedUserId = command.assignedUserId() == null ? null
                : requireAssigneeInBranch(command.assignedUserId(), companyId, branchId);

        FollowUpType followUpType = command.followUpType() == null ? FollowUpType.GENERAL : command.followUpType();
        FollowUpType referenceType = command.referenceType() == null ? followUpType : command.referenceType();
        FollowUpPriority priority = command.priority() == null ? FollowUpPriority.MEDIUM : command.priority();

        FollowUp followUp = FollowUp.builder()
                .branchId(branchId)
                .referenceType(referenceType)
                .referenceId(command.referenceId())
                .customerId(command.customerId())
                .shipmentId(command.shipmentId())
                .assignedUserId(assignedUserId)
                .title(title)
                .description(trimOrNull(command.description()))
                .followUpType(followUpType)
                .priority(priority)
                .status(FollowUpStatus.OPEN)
                .dueDate(command.dueDate())
                .build();
        FollowUp saved = followUpRepository.save(followUp);

        writeHistory(saved, FollowUpHistoryAction.CREATED, null, saved.getStatus(), null, null,
                assignedUserId, "Follow-up created", caller.userId());

        auditService.record(AuditAction.FOLLOWUP_CREATED, ENTITY, saved.getId(),
                Map.of("title", title, "priority", priority.name(), "branchId", branchId.toString()));

        if (assignedUserId != null) {
            notifyAssignment(saved, caller.userId());
        }
        return saved;
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public FollowUp getById(UUID id) {
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        return followUp;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowUp> search(FollowUpCriteria criteria, Pageable pageable) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = requireCompany();
        FollowUpCriteria safe = (criteria == null ? FollowUpCriteria.none() : criteria).scopedTo(companyId);

        if (!isCompanyAdmin(caller)) {
            UUID ownBranch = ownBranch(caller, companyId).orElse(null);
            safe = safe.restrictedTo(caller.userId(), ownBranch);
        }
        return followUpRepository.findAll(FollowUpSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpHistory> history(UUID id) {
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        return historyRepository.findByFollowUp(id, followUp.getCompanyId());
    }

    // ------------------------------------------------------------------ update

    @Override
    @Transactional
    public FollowUp update(UUID id, UpdateFollowUpCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = requireCompany();
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        requireEditable(followUp, caller);

        if (command.version() != null && !Objects.equals(followUp.getVersion(), command.version())) {
            throw new ObjectOptimisticLockingFailureException(FollowUp.class, followUp.getId());
        }
        String title = requireText(command.title(), "Title");
        if (command.dueDate() == null) {
            throw new BusinessRuleException("A due date is required.");
        }
        UUID branchId = command.branchId() == null ? followUp.getBranchId()
                : resolveBranchForWrite(caller, companyId, command.branchId());

        followUp.setBranchId(branchId);
        followUp.setTitle(title);
        followUp.setDescription(trimOrNull(command.description()));
        followUp.setDueDate(command.dueDate());
        followUp.setNextFollowUpDate(command.dueDate());
        followUp.setCustomerId(command.customerId());
        followUp.setShipmentId(command.shipmentId());
        if (command.followUpType() != null) {
            followUp.setFollowUpType(command.followUpType());
        }
        if (command.referenceType() != null) {
            followUp.setReferenceType(command.referenceType());
        }
        if (command.referenceId() != null) {
            followUp.setReferenceId(command.referenceId());
        }
        if (command.priority() != null) {
            followUp.setPriority(command.priority());
        }
        followUp.resetSweepFlags();
        FollowUp saved = followUpRepository.save(followUp);

        writeHistory(saved, FollowUpHistoryAction.UPDATED, null, null, null, null, null,
                "Follow-up details updated", caller.userId());
        auditService.record(AuditAction.FOLLOWUP_UPDATED, ENTITY, saved.getId(), Map.of("title", title));
        return saved;
    }

    // ------------------------------------------------------------------ assignment

    @Override
    @Transactional
    @PreAuthorize(ASSIGN_ROLES)
    public FollowUp assign(UUID id, UUID assignedUserId, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = requireCompany();
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        requireNotTerminal(followUp);
        requireManagesBranch(caller, companyId, followUp.getBranchId());

        UUID resolved = requireAssigneeInBranch(assignedUserId, companyId, followUp.getBranchId());
        followUp.setAssignedUserId(resolved);
        FollowUp saved = followUpRepository.save(followUp);

        writeHistory(saved, FollowUpHistoryAction.ASSIGNED, null, null, null, null,
                resolved, trimOrNull(remarks), caller.userId());
        auditService.record(AuditAction.FOLLOWUP_ASSIGNED, ENTITY, saved.getId(),
                Map.of("assignedUserId", resolved.toString()));
        notifyAssignment(saved, caller.userId());
        return saved;
    }

    // ------------------------------------------------------------------ status / reschedule / notes

    @Override
    @Transactional
    public FollowUp changeStatus(UUID id, FollowUpStatus status, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        requireStaffOn(followUp, caller);

        if (status == null) {
            throw new BusinessRuleException("A status is required.");
        }
        if (status == FollowUpStatus.RESCHEDULED) {
            throw new BusinessRuleException("Use the dedicated reschedule action for this transition.");
        }
        FollowUpStatus from = followUp.getStatus();
        if (!from.canTransitionTo(status)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot move a follow-up from %s to %s.".formatted(from, status));
        }
        followUp.setStatus(status);
        if (status == FollowUpStatus.COMPLETED) {
            followUp.setCompletedAt(Instant.now());
            followUp.setCompletedBy(caller.userId());
        }
        FollowUp saved = followUpRepository.save(followUp);

        writeHistory(saved, FollowUpHistoryAction.STATUS_CHANGED, from, status, null, null, null,
                trimOrNull(remarks), caller.userId());
        auditService.record(AuditAction.FOLLOWUP_STATUS_CHANGED, ENTITY, saved.getId(),
                Map.of("status", status.name()));
        return saved;
    }

    @Override
    @Transactional
    public FollowUp reschedule(UUID id, RescheduleCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        requireStaffOn(followUp, caller);
        requireNotTerminal(followUp);

        if (command.newDueDate() == null) {
            throw new BusinessRuleException("A new due date is required to reschedule.");
        }
        FollowUpStatus from = followUp.getStatus();
        Instant previousDueDate = followUp.getDueDate();

        followUp.setStatus(FollowUpStatus.RESCHEDULED);
        followUp.setNextFollowUpDate(command.newDueDate());
        followUp.setDueDate(command.newDueDate());
        followUp.resetSweepFlags();
        FollowUp saved = followUpRepository.save(followUp);

        writeHistory(saved, FollowUpHistoryAction.RESCHEDULED, from, FollowUpStatus.RESCHEDULED,
                previousDueDate, command.newDueDate(), null, trimOrNull(command.reason()), caller.userId());
        auditService.record(AuditAction.FOLLOWUP_RESCHEDULED, ENTITY, saved.getId(),
                Map.of("newDueDate", command.newDueDate().toString()));
        return saved;
    }

    @Override
    @Transactional
    public FollowUpHistory addNote(UUID id, String note) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        FollowUp followUp = loadForRead(id);
        requireVisible(followUp);
        requireStaffOn(followUp, caller);

        String text = requireText(note, "Note");
        FollowUpHistory entry = writeHistory(followUp, FollowUpHistoryAction.NOTE_ADDED, null, null, null, null,
                null, text, caller.userId());
        auditService.record(AuditAction.FOLLOWUP_NOTE_ADDED, ENTITY, followUp.getId(), Map.of());
        return entry;
    }

    // ------------------------------------------------------------------ dashboard

    @Override
    @Transactional(readOnly = true)
    public FollowUpDashboardStats dashboard() {
        UUID companyId = requireCompany();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return new FollowUpDashboardStats(
                followUpRepository.countOverdue(companyId, now),
                followUpRepository.countDueToday(companyId, startOfToday, startOfTomorrow),
                followUpRepository.countUpcoming(companyId, startOfTomorrow),
                followUpRepository.countUrgent(companyId));
    }

    // ------------------------------------------------------------------ scoping / helpers

    private FollowUp loadForRead(UUID id) {
        UUID companyId = requireCompany();
        return followUpRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /** Branch users see only their own branch's follow-ups (business rule); a company
     *  admin sees every branch. The assignee/creator exception mirrors Ticket Support's
     *  own requester/assignee visibility, since a task can be assigned across branches
     *  by a manager even though day-to-day scoping is per-branch. */
    private void requireVisible(FollowUp followUp) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (isCompanyAdmin(caller)) {
            return;
        }
        if (followUp.isAssignee(caller.userId()) || caller.userId().equals(followUp.getCreatedBy())) {
            return;
        }
        UUID ownBranch = ownBranch(caller, followUp.getCompanyId()).orElse(null);
        if (ownBranch != null && ownBranch.equals(followUp.getBranchId())) {
            return;
        }
        throw new ResourceNotFoundException(ENTITY, followUp.getId());
    }

    private void requireEditable(FollowUp followUp, AuthenticatedUser caller) {
        if (followUp.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    "A completed or cancelled follow-up cannot be edited — see its history instead.");
        }
        requireStaffOn(followUp, caller);
    }

    private void requireNotTerminal(FollowUp followUp) {
        if (followUp.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    "A completed or cancelled follow-up cannot be changed further.");
        }
    }

    /** True when the caller may act on this follow-up: its own assignee, whoever created
     *  it, a company admin, or the branch manager of the branch it belongs to. */
    private void requireStaffOn(FollowUp followUp, AuthenticatedUser caller) {
        if (isCompanyAdmin(caller) || followUp.isAssignee(caller.userId())
                || caller.userId().equals(followUp.getCreatedBy())) {
            return;
        }
        UUID ownBranch = ownBranch(caller, followUp.getCompanyId()).orElse(null);
        if (ownBranch != null && ownBranch.equals(followUp.getBranchId())) {
            return;
        }
        throw new ForbiddenException("Only the assignee, the creator, or this branch's manager may do this.");
    }

    /** A non-admin may only create/move a follow-up into their own (or managed) branch;
     *  COMPANY_ADMIN/HUB_MANAGER may name any real branch in the company. */
    private UUID resolveBranchForWrite(AuthenticatedUser caller, UUID companyId, UUID requestedBranchId) {
        if (isCompanyAdmin(caller) || caller.hasRole(Roles.HUB_MANAGER)) {
            UUID branchId = requestedBranchId;
            if (branchId == null) {
                throw new BusinessRuleException("A branch is required.");
            }
            if (!directory.branchExists(branchId, companyId)) {
                throw new ResourceNotFoundException("Branch", branchId);
            }
            return branchId;
        }
        UUID ownBranch = ownBranch(caller, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "You are not placed at a branch, so you cannot create a follow-up."));
        if (requestedBranchId != null && !requestedBranchId.equals(ownBranch)) {
            throw new ForbiddenException("You may only create follow-ups for your own branch.");
        }
        return ownBranch;
    }

    private void requireManagesBranch(AuthenticatedUser caller, UUID companyId, UUID branchId) {
        if (isCompanyAdmin(caller)) {
            return;
        }
        UUID ownBranch = ownBranch(caller, companyId).orElse(null);
        if (ownBranch == null || !ownBranch.equals(branchId)) {
            throw new ForbiddenException("You may only assign follow-ups within your own branch.");
        }
    }

    private UUID requireAssigneeInBranch(UUID assignedUserId, UUID companyId, UUID branchId) {
        directory.findUser(assignedUserId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignedUserId));
        UUID placedAt = directory.branchOfUser(assignedUserId, companyId).orElse(null);
        UUID manages = directory.branchManagedBy(assignedUserId, companyId).orElse(null);
        if (!branchId.equals(placedAt) && !branchId.equals(manages)) {
            throw new BusinessRuleException("The assigned user must belong to this follow-up's branch.");
        }
        return assignedUserId;
    }

    private Optional<UUID> ownBranch(AuthenticatedUser caller, UUID companyId) {
        Optional<UUID> placement = directory.branchOfUser(caller.userId(), companyId);
        if (placement.isPresent()) {
            return placement;
        }
        return directory.branchManagedBy(caller.userId(), companyId);
    }

    private boolean isCompanyAdmin(AuthenticatedUser caller) {
        return caller.hasRole(Roles.COMPANY_ADMIN) || caller.isSuperAdmin();
    }

    private FollowUpHistory writeHistory(FollowUp followUp, FollowUpHistoryAction action,
                                          FollowUpStatus from, FollowUpStatus to,
                                          Instant previousDueDate, Instant newDueDate,
                                          UUID assignedToUserId, String note, UUID actorId) {
        return historyRepository.save(FollowUpHistory.builder()
                .followUpId(followUp.getId())
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .previousDueDate(previousDueDate)
                .newDueDate(newDueDate)
                .assignedToUserId(assignedToUserId)
                .note(note)
                .changedByUserId(actorId)
                .build());
    }

    private void notifyAssignment(FollowUp followUp, UUID actorId) {
        if (followUp.getAssignedUserId() == null || followUp.getAssignedUserId().equals(actorId)) {
            return;
        }
        notificationService.notifyFollowUp(followUp.getAssignedUserId(), NotificationType.FOLLOWUP_ASSIGNED,
                followUp.getTitle(), "A follow-up was assigned to you.", followUp.getId());
        if (followUp.getPriority() == FollowUpPriority.URGENT) {
            notificationService.notifyFollowUp(followUp.getAssignedUserId(), NotificationType.FOLLOWUP_URGENT,
                    followUp.getTitle(), "This follow-up is urgent.", followUp.getId());
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(label + " is required.");
        }
        return value.trim();
    }

    private static String trimOrNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. A follow-up belongs to a company, so this "
                        + "operation must be performed by a user of that company."));
    }
}
