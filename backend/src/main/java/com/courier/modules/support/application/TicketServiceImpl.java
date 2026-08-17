package com.courier.modules.support.application;

import com.courier.modules.support.application.command.AssignmentCommand;
import com.courier.modules.support.application.command.CreateTicketCommand;
import com.courier.modules.support.application.command.ReplyCommand;
import com.courier.modules.support.application.command.UploadTicketAttachmentCommand;
import com.courier.modules.support.domain.CompanyTicketSequenceRepository;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketAssignmentAction;
import com.courier.modules.support.domain.TicketAssignmentHistory;
import com.courier.modules.support.domain.TicketAssignmentHistoryRepository;
import com.courier.modules.support.domain.TicketAttachment;
import com.courier.modules.support.domain.TicketAttachmentRepository;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketCriteria;
import com.courier.modules.support.domain.TicketDirectoryPort;
import com.courier.modules.support.domain.TicketMessage;
import com.courier.modules.support.domain.TicketMessageRepository;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketRepository;
import com.courier.modules.support.domain.NotificationType;
import com.courier.modules.support.domain.TicketSlaRule;
import com.courier.modules.support.domain.TicketSlaRuleRepository;
import com.courier.modules.support.domain.TicketSpecifications;
import com.courier.modules.support.domain.TicketStatus;
import com.courier.modules.support.domain.TicketStatusHistory;
import com.courier.modules.support.domain.TicketStatusHistoryRepository;
import com.courier.modules.support.domain.TicketSubCategoryRepository;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * See {@link TicketService}. Scoping is hand-rolled per method, mirroring {@code
 * WalletTopupRequestServiceImpl} — this codebase has no generic "requireVisible" helper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final String ENTITY = "Ticket";

    private static final String ADMIN_ONLY =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')";

    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "heic", "pdf", "doc", "docx", "xls", "xlsx");
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketStatusHistoryRepository statusHistoryRepository;
    private final TicketAssignmentHistoryRepository assignmentHistoryRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketSubCategoryRepository subCategoryRepository;
    private final CompanyTicketSequenceRepository sequenceRepository;
    private final TicketDirectoryPort directory;
    private final FileStoragePort fileStoragePort;
    private final AuditService auditService;
    private final TicketSlaRuleRepository slaRuleRepository;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ create

    @Override
    @Transactional
    public Ticket create(CreateTicketCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = resolveCompanyForCreate(caller, command.companyId());

        String subject = requireText(command.subject(), "Subject");
        String description = requireText(command.description(), "Description");
        if (command.categoryId() == null) {
            throw new BusinessRuleException("A category is required.");
        }
        categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("TicketCategory", command.categoryId()));
        if (command.subCategoryId() != null) {
            subCategoryRepository.findById(command.subCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("TicketSubCategory", command.subCategoryId()));
        }
        if (command.relatedBranchId() != null && !directory.branchExists(command.relatedBranchId(), companyId)) {
            throw new ResourceNotFoundException("Branch", command.relatedBranchId());
        }
        TicketPriority priority = command.priority() == null ? TicketPriority.MEDIUM : command.priority();
        Instant now = Instant.now();
        TicketSlaRule slaRule = slaRuleRepository
                .findByCompanyIdAndPriorityAndActiveTrue(companyId, priority).orElse(null);

        Ticket ticket = Ticket.builder()
                .ticketNumber(nextTicketNumber(companyId))
                .subject(subject)
                .description(description)
                .categoryId(command.categoryId())
                .subCategoryId(command.subCategoryId())
                .priority(priority)
                .status(TicketStatus.OPEN)
                .relatedShipmentId(command.relatedShipmentId())
                .relatedCustomerId(command.relatedCustomerId())
                .relatedBranchId(command.relatedBranchId())
                .createdByUserId(caller.userId())
                .escalated(false)
                .slaFirstResponseDueAt(slaRule == null ? null : now.plusSeconds(slaRule.getFirstResponseMinutes() * 60L))
                .slaResolutionDueAt(slaRule == null ? null : now.plusSeconds(slaRule.getResolutionMinutes() * 60L))
                .build();
        Ticket saved = ticketRepository.save(ticket);

        writeStatusHistory(saved, null, TicketStatus.OPEN, caller.userId(), "Ticket raised");

        log.info("Ticket {} ({}) raised by {}", saved.getId(), saved.getTicketNumber(), currentActor());
        auditService.record(AuditAction.TICKET_CREATED, ENTITY, saved.getId(),
                Map.of("ticketNumber", saved.getTicketNumber(), "priority", priority.name()));

        return saved;
    }

    @Override
    @Transactional
    public Ticket raiseSystemTicket(CreateTicketCommand command, UUID assigneeUserId) {
        UUID companyId = requireCompany();

        String subject = requireText(command.subject(), "Subject");
        String description = requireText(command.description(), "Description");
        if (command.categoryId() == null) {
            throw new BusinessRuleException("A category is required.");
        }
        categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("TicketCategory", command.categoryId()));
        if (command.relatedBranchId() != null && !directory.branchExists(command.relatedBranchId(), companyId)) {
            throw new ResourceNotFoundException("Branch", command.relatedBranchId());
        }
        TicketPriority priority = command.priority() == null ? TicketPriority.MEDIUM : command.priority();
        Instant now = Instant.now();
        TicketSlaRule slaRule = slaRuleRepository
                .findByCompanyIdAndPriorityAndActiveTrue(companyId, priority).orElse(null);

        Ticket ticket = Ticket.builder()
                .ticketNumber(nextTicketNumber(companyId))
                .subject(subject)
                .description(description)
                .categoryId(command.categoryId())
                .subCategoryId(command.subCategoryId())
                .priority(priority)
                .status(TicketStatus.OPEN)
                .relatedShipmentId(command.relatedShipmentId())
                .relatedCustomerId(command.relatedCustomerId())
                .relatedBranchId(command.relatedBranchId())
                .createdByUserId(null)
                .escalated(false)
                .slaFirstResponseDueAt(slaRule == null ? null : now.plusSeconds(slaRule.getFirstResponseMinutes() * 60L))
                .slaResolutionDueAt(slaRule == null ? null : now.plusSeconds(slaRule.getResolutionMinutes() * 60L))
                .build();
        Ticket saved = ticketRepository.save(ticket);

        writeStatusHistory(saved, null, TicketStatus.OPEN, null, "Auto-raised: SLA breach");
        auditService.record(AuditAction.TICKET_CREATED, ENTITY, saved.getId(),
                Map.of("ticketNumber", saved.getTicketNumber(), "priority", priority.name(), "system", true));

        UUID assigneeId = assigneeUserId != null && directory.findUser(assigneeUserId, companyId).isPresent()
                ? assigneeUserId
                : null;
        if (assigneeId != null) {
            saved.setAssigneeUserId(assigneeId);
            transitionTo(saved, TicketStatus.ASSIGNED, null, "Auto-assigned: branch manager");
            saved = ticketRepository.save(saved);
            writeAssignmentHistory(saved, assigneeId, null, TicketAssignmentAction.ASSIGNED,
                    "Auto-assigned to branch manager");
            auditService.record(AuditAction.TICKET_ASSIGNED, ENTITY, saved.getId(),
                    Map.of("assigneeUserId", assigneeId.toString(), "system", true));
            notificationService.notify(assigneeId, NotificationType.TICKET_ASSIGNED,
                    saved.getTicketNumber(), "An SLA breach ticket was auto-assigned to you.", saved.getId());
        }

        log.info("System-raised ticket {} ({})", saved.getId(), saved.getTicketNumber());
        return saved;
    }

    private UUID resolveCompanyForCreate(AuthenticatedUser caller, UUID requestedCompanyId) {
        if (caller.isSuperAdmin()) {
            if (requestedCompanyId == null) {
                throw new BusinessRuleException("SUPER_ADMIN must name the company this ticket belongs to.");
            }
            return requestedCompanyId;
        }
        return requireCompany();
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public Ticket getById(UUID id) {
        Ticket ticket = loadForRead(id);
        requireVisible(ticket);
        return ticket;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> search(TicketCriteria criteria, Pageable pageable) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        TicketCriteria safe = criteria == null ? TicketCriteria.none() : criteria;

        if (caller.isSuperAdmin()) {
            if (safe.companyId() != null) {
                TicketCriteria scoped = safe.scopedTo(safe.companyId());
                return ticketRepository.findAll(TicketSpecifications.matching(scoped), pageable);
            }
            // Genuinely cross-tenant: SUPER_ADMIN's own JWT still carries a (sentinel)
            // company id, so CompanyContext is never actually empty for this role — the
            // Hibernate companyFilter would otherwise silently restrict every row to that
            // sentinel. runAs(null, ...) is the platform's own sanctioned way to disable
            // it for the duration of one call (see CompanyContext's own class doc).
            TicketCriteria crossTenant = safe.crossTenantScope();
            return CompanyContext.runAs(null,
                    () -> ticketRepository.findAll(TicketSpecifications.matching(crossTenant), pageable));
        }

        UUID companyId = requireCompany();
        safe = safe.scopedTo(companyId);
        if (!isCompanyAdmin(caller)) {
            UUID ownBranch = ownBranch(caller, companyId).orElse(null);
            safe = safe.restrictedTo(caller.userId(), ownBranch);
        }
        return ticketRepository.findAll(TicketSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketMessage> messages(UUID ticketId) {
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        List<TicketMessage> all = messageRepository.findByTicket(ticketId, ticket.getCompanyId());
        if (isStaffOn(ticket)) {
            return all;
        }
        return all.stream().filter(m -> !m.isInternalNote()).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketAttachment> attachments(UUID ticketId) {
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        return attachmentRepository.findByTicket(ticketId, ticket.getCompanyId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketStatusHistory> statusHistory(UUID ticketId) {
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        return statusHistoryRepository.findByTicket(ticketId, ticket.getCompanyId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketAssignmentHistory> assignmentHistory(UUID ticketId) {
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        return assignmentHistoryRepository.findByTicket(ticketId, ticket.getCompanyId());
    }

    // ------------------------------------------------------------------ conversation

    @Override
    @Transactional
    public TicketMessage reply(UUID ticketId, ReplyCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        boolean staff = isStaffOn(ticket);
        if (command.internalNote() && !staff) {
            throw new ForbiddenException("Only staff on this ticket may add an internal note.");
        }
        String body = requireText(command.body(), "Message");

        TicketMessage message = messageRepository.save(TicketMessage.builder()
                .ticketId(ticket.getId())
                .authorUserId(caller.userId())
                .body(body)
                .internalNote(command.internalNote())
                .build());

        if (staff && !command.internalNote() && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(Instant.now());
            ticketRepository.save(ticket);
        }

        if (command.internalNote()) {
            if (ticket.getAssigneeUserId() != null && !caller.userId().equals(ticket.getAssigneeUserId())) {
                notificationService.notify(ticket.getAssigneeUserId(), NotificationType.INTERNAL_UPDATE,
                        ticket.getTicketNumber(), "A new internal note was added.", ticket.getId());
            }
        } else if (staff) {
            notificationService.notify(ticket.getCreatedByUserId(), NotificationType.NEW_REPLY,
                    ticket.getTicketNumber(), "Support replied to your ticket.", ticket.getId());
        } else if (ticket.getAssigneeUserId() != null) {
            notificationService.notify(ticket.getAssigneeUserId(), NotificationType.NEW_REPLY,
                    ticket.getTicketNumber(), "The requester replied.", ticket.getId());
        }

        auditService.record(AuditAction.TICKET_MESSAGE_ADDED, ENTITY, ticket.getId(),
                Map.of("internalNote", command.internalNote()));

        return message;
    }

    @Override
    @Transactional
    public TicketAttachment uploadAttachment(UUID ticketId, UploadTicketAttachmentCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        String extension = extensionOf(command.filename());
        if (!ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG/PNG/WEBP/HEIC images, PDF, or Office documents are accepted.");
        }
        if (command.content() == null || command.content().length == 0) {
            throw new BusinessRuleException("The file is empty.");
        }
        if (command.content().length > MAX_ATTACHMENT_BYTES) {
            throw new BusinessRuleException(ErrorCode.FILE_TOO_LARGE, "Attachments are limited to 10 MB.");
        }

        String key = "%s/%s/%s-%s.%s".formatted(ticket.getCompanyId(), ticket.getId(),
                UUID.randomUUID(), sanitise(command.filename()), extension);
        FileStoragePort.StoredFile stored = fileStoragePort.upload(new FileStoragePort.UploadRequest(
                command.content(), key, command.contentType(), "ticket-attachment"));

        TicketAttachment attachment = attachmentRepository.save(TicketAttachment.builder()
                .ticketId(ticket.getId())
                .messageId(command.messageId())
                .assetUrl(stored.url())
                .storageKey(stored.key())
                .filename(command.filename())
                .contentType(command.contentType())
                .sizeBytes(command.content().length)
                .uploadedByUserId(caller.userId())
                .build());

        auditService.record(AuditAction.TICKET_ATTACHMENT_UPLOADED, ENTITY, ticket.getId(),
                Map.of("filename", command.filename()));

        return attachment;
    }

    // ------------------------------------------------------------------ assignment

    @Override
    @Transactional
    @PreAuthorize(ADMIN_ONLY)
    public Ticket assign(UUID ticketId, AssignmentCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BusinessRuleException(
                    "This ticket is already assigned; use reassign to change the owner.");
        }
        UUID assigneeId = requireAssignee(command.assigneeUserId(), ticket.getCompanyId());

        ticket.setAssigneeUserId(assigneeId);
        transitionTo(ticket, TicketStatus.ASSIGNED, caller.userId(), command.remarks());
        Ticket saved = ticketRepository.save(ticket);

        writeAssignmentHistory(saved, assigneeId, caller.userId(), TicketAssignmentAction.ASSIGNED, command.remarks());
        auditService.record(AuditAction.TICKET_ASSIGNED, ENTITY, saved.getId(),
                Map.of("assigneeUserId", assigneeId.toString()));
        notificationService.notify(assigneeId, NotificationType.TICKET_ASSIGNED,
                saved.getTicketNumber(), "A ticket was assigned to you.", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN_ONLY)
    public Ticket reassign(UUID ticketId, AssignmentCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        if (ticket.getStatus().isTerminal() || ticket.getStatus() == TicketStatus.OPEN) {
            throw new BusinessRuleException(
                    "A ticket must first be assigned before it can be reassigned, and cannot be "
                            + "reassigned once resolved or closed.");
        }
        UUID assigneeId = requireAssignee(command.assigneeUserId(), ticket.getCompanyId());

        ticket.setAssigneeUserId(assigneeId);
        Ticket saved = ticketRepository.save(ticket);

        writeAssignmentHistory(saved, assigneeId, caller.userId(), TicketAssignmentAction.REASSIGNED, command.remarks());
        auditService.record(AuditAction.TICKET_REASSIGNED, ENTITY, saved.getId(),
                Map.of("assigneeUserId", assigneeId.toString()));
        notificationService.notify(assigneeId, NotificationType.TICKET_REASSIGNED,
                saved.getTicketNumber(), "A ticket was reassigned to you.", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN_ONLY)
    public Ticket escalate(UUID ticketId, AssignmentCommand command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        UUID assigneeId = command.assigneeUserId() != null
                ? requireAssignee(command.assigneeUserId(), ticket.getCompanyId())
                : ticket.getAssigneeUserId();
        if (assigneeId == null) {
            throw new BusinessRuleException("Name who this escalates to.");
        }

        ticket.setAssigneeUserId(assigneeId);
        ticket.setEscalated(true);
        Ticket saved = ticketRepository.save(ticket);

        writeAssignmentHistory(saved, assigneeId, caller.userId(), TicketAssignmentAction.ESCALATED, command.remarks());
        auditService.record(AuditAction.TICKET_ESCALATED, ENTITY, saved.getId(),
                Map.of("assigneeUserId", assigneeId.toString()));
        notificationService.notify(assigneeId, NotificationType.TICKET_ESCALATED,
                saved.getTicketNumber(), "This ticket was escalated to you.", saved.getId());
        return saved;
    }

    // ------------------------------------------------------------------ status / priority / category

    @Override
    @Transactional
    public Ticket changeStatus(UUID ticketId, TicketStatus status, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        requireStaffOn(ticket, caller);

        if (status == TicketStatus.REOPENED || status == TicketStatus.CLOSED) {
            throw new BusinessRuleException("Use the dedicated reopen/close action for this transition.");
        }
        transitionTo(ticket, status, caller.userId(), remarks);
        Ticket saved = ticketRepository.save(ticket);

        auditService.record(AuditAction.TICKET_STATUS_CHANGED, ENTITY, saved.getId(),
                Map.of("status", status.name()));
        if (status == TicketStatus.RESOLVED) {
            notificationService.notify(saved.getCreatedByUserId(), NotificationType.TICKET_RESOLVED,
                    saved.getTicketNumber(), "Your ticket was marked resolved.", saved.getId());
        } else {
            notificationService.notify(saved.getCreatedByUserId(), NotificationType.STATUS_CHANGED,
                    saved.getTicketNumber(), "Status changed to " + label(status) + ".", saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public Ticket changePriority(UUID ticketId, TicketPriority priority, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        requireStaffOn(ticket, caller);
        if (priority == null) {
            throw new BusinessRuleException("Priority is required.");
        }

        ticket.setPriority(priority);
        Ticket saved = ticketRepository.save(ticket);

        auditService.record(AuditAction.TICKET_PRIORITY_CHANGED, ENTITY, saved.getId(),
                Map.of("priority", priority.name(), "remarks", remarks == null ? "" : remarks));
        notificationService.notify(saved.getCreatedByUserId(), NotificationType.PRIORITY_CHANGED,
                saved.getTicketNumber(), "Priority changed to " + priority.name() + ".", saved.getId());
        if (saved.getAssigneeUserId() != null) {
            notificationService.notify(saved.getAssigneeUserId(), NotificationType.PRIORITY_CHANGED,
                    saved.getTicketNumber(), "Priority changed to " + priority.name() + ".", saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public Ticket changeCategory(UUID ticketId, UUID categoryId, UUID subCategoryId, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        requireStaffOn(ticket, caller);
        if (categoryId == null) {
            throw new BusinessRuleException("A category is required.");
        }
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketCategory", categoryId));
        if (subCategoryId != null) {
            subCategoryRepository.findById(subCategoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("TicketSubCategory", subCategoryId));
        }

        ticket.setCategoryId(categoryId);
        ticket.setSubCategoryId(subCategoryId);
        Ticket saved = ticketRepository.save(ticket);

        auditService.record(AuditAction.TICKET_CATEGORY_CHANGED, ENTITY, saved.getId(),
                Map.of("categoryId", categoryId.toString(), "remarks", remarks == null ? "" : remarks));
        return saved;
    }

    @Override
    @Transactional
    public Ticket reopen(UUID ticketId, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);

        boolean allowed = ticket.isRequester(caller.userId()) || isCompanyAdmin(caller) || caller.isSuperAdmin();
        if (!allowed) {
            throw new ForbiddenException("Only the requester may reopen this ticket.");
        }

        transitionTo(ticket, TicketStatus.REOPENED, caller.userId(), remarks);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        Ticket saved = ticketRepository.save(ticket);

        log.info("Ticket {} reopened by {}", saved.getTicketNumber(), currentActor());
        auditService.record(AuditAction.TICKET_REOPENED, ENTITY, saved.getId(),
                Map.of("remarks", remarks == null ? "" : remarks));
        if (saved.getAssigneeUserId() != null) {
            notificationService.notify(saved.getAssigneeUserId(), NotificationType.TICKET_REOPENED,
                    saved.getTicketNumber(), "The requester reopened this ticket.", saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public Ticket close(UUID ticketId, String remarks) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Ticket ticket = loadForRead(ticketId);
        requireVisible(ticket);
        requireStaffOn(ticket, caller);

        transitionTo(ticket, TicketStatus.CLOSED, caller.userId(), remarks);
        Ticket saved = ticketRepository.save(ticket);

        auditService.record(AuditAction.TICKET_CLOSED, ENTITY, saved.getId(),
                Map.of("remarks", remarks == null ? "" : remarks));
        notificationService.notify(saved.getCreatedByUserId(), NotificationType.TICKET_CLOSED,
                saved.getTicketNumber(), "Your ticket was closed.", saved.getId());
        return saved;
    }

    // ------------------------------------------------------------------ dashboard

    @Override
    @Transactional(readOnly = true)
    public TicketDashboardStats dashboard(UUID companyId) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (!caller.isSuperAdmin() && !caller.hasRole(Roles.COMPANY_ADMIN)) {
            throw new ForbiddenException("Only a company admin or SUPER_ADMIN may view the ticket dashboard.");
        }
        boolean crossTenant = caller.isSuperAdmin() && companyId == null;
        UUID scope = crossTenant ? null : (caller.isSuperAdmin() ? companyId : requireCompany());

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant since30d = Instant.now().minus(Duration.ofDays(30));

        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        Map<TicketPriority, Long> byPriority = new EnumMap<>(TicketPriority.class);
        Map<UUID, Long> byCategory = new HashMap<>();
        Map<UUID, Long> byBranch = new HashMap<>();
        Map<UUID, Long> byAgent = new HashMap<>();
        Map<UUID, Long> byTenant = new HashMap<>();
        long total;
        long resolvedToday;
        long closedToday;
        List<Ticket> recent;

        // SUPER_ADMIN's own JWT still carries a (sentinel) company id, so CompanyContext
        // is never actually empty/matching for this role — every count below would
        // otherwise silently run against the sentinel via the Hibernate companyFilter.
        // runAs rebinds it to the real scope (null = genuinely cross-tenant) for the
        // duration of these reads, the platform's own sanctioned escape hatch.
        if (crossTenant) {
            DashboardCounts counts = CompanyContext.runAs(null, () -> {
                ticketRepository.countByTenantAll().forEach(c -> byTenant.put(c.getTenantId(), c.getTotal()));
                long tenantTotal = byTenant.values().stream().mapToLong(Long::longValue).sum();
                return new DashboardCounts(tenantTotal, 0, 0, List.of());
            });
            total = counts.total();
            resolvedToday = counts.resolvedToday();
            closedToday = counts.closedToday();
            recent = counts.recent();
        } else {
            DashboardCounts counts = CompanyContext.runAs(scope, () -> {
                ticketRepository.countByStatus(scope).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
                ticketRepository.countByPriority(scope).forEach(c -> byPriority.put(c.getPriority(), c.getTotal()));
                ticketRepository.countByCategory(scope).forEach(c -> byCategory.put(c.getCategoryId(), c.getTotal()));
                ticketRepository.countByBranch(scope).forEach(c -> byBranch.put(c.getBranchId(), c.getTotal()));
                ticketRepository.countByAgent(scope).forEach(c -> byAgent.put(c.getAgentId(), c.getTotal()));
                return new DashboardCounts(
                        ticketRepository.countAll(scope),
                        ticketRepository.countResolvedSince(scope, startOfToday),
                        ticketRepository.countClosedSince(scope, startOfToday),
                        ticketRepository.findCreatedSince(scope, since30d));
            });
            total = counts.total();
            resolvedToday = counts.resolvedToday();
            closedToday = counts.closedToday();
            recent = counts.recent();
        }

        Double avgResolutionHours = averageResolutionHours(recent);
        List<TicketDashboardStats.DailyCount> trend = volumeTrend(recent);

        long slaBreachedCount = 0;
        Map<String, Long> slaPerformance = new HashMap<>();
        if (!crossTenant) {
            Instant now = Instant.now();
            List<Ticket> open = CompanyContext.runAs(scope, () -> ticketRepository.findOpenTickets(scope));
            Map<String, Long> buckets = open.stream()
                    .collect(Collectors.groupingBy(t -> slaBucket(t, now), Collectors.counting()));
            slaPerformance.putAll(buckets);
            slaBreachedCount = buckets.getOrDefault("BREACHED", 0L);
        }

        return new TicketDashboardStats(
                total,
                byStatus.getOrDefault(TicketStatus.OPEN, 0L),
                byStatus.getOrDefault(TicketStatus.ASSIGNED, 0L),
                byStatus.getOrDefault(TicketStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(TicketStatus.WAITING_FOR_USER, 0L),
                byStatus.getOrDefault(TicketStatus.WAITING_FOR_INTERNAL_TEAM, 0L),
                byPriority.getOrDefault(TicketPriority.CRITICAL, 0L),
                resolvedToday,
                closedToday,
                slaBreachedCount,
                avgResolutionHours,
                byStatus, byPriority, byCategory, byBranch, byAgent, byTenant, slaPerformance, trend);
    }

    /** ON_TRACK / WARNING / BREACHED / MET / NO_SLA — shared by the dashboard's SLA
     *  performance chart and {@code TicketMapper}'s per-ticket SLA status. A closed/
     *  resolved ticket resolves to MET or BREACHED (compared against when it actually
     *  finished), never ON_TRACK/WARNING — those only make sense for an open ticket. */
    public static String slaBucket(Ticket ticket, Instant now) {
        Instant due = ticket.getSlaResolutionDueAt();
        if (due == null) {
            return "NO_SLA";
        }
        if (ticket.getStatus().isTerminal()) {
            Instant finishedAt = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getClosedAt();
            return finishedAt != null && finishedAt.isAfter(due) ? "BREACHED" : "MET";
        }
        if (now.isAfter(due)) {
            return "BREACHED";
        }
        long totalMinutes = Duration.between(ticket.getCreatedAt(), due).toMinutes();
        long remainingMinutes = Duration.between(now, due).toMinutes();
        if (totalMinutes > 0 && remainingMinutes <= Math.max(1, totalMinutes / 5)) {
            return "WARNING";
        }
        return "ON_TRACK";
    }

    private record DashboardCounts(long total, long resolvedToday, long closedToday, List<Ticket> recent) {
    }

    private Double averageResolutionHours(List<Ticket> tickets) {
        List<Double> hours = tickets.stream()
                .filter(t -> t.getResolvedAt() != null)
                .map(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes() / 60.0)
                .toList();
        if (hours.isEmpty()) {
            return null;
        }
        return hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private List<TicketDashboardStats.DailyCount> volumeTrend(List<Ticket> tickets) {
        Map<String, Long> byDay = tickets.stream().collect(Collectors.groupingBy(
                t -> DAY.format(t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()),
                Collectors.counting()));
        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TicketDashboardStats.DailyCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ transitions

    private void transitionTo(Ticket ticket, TicketStatus target, UUID actorId, String remarks) {
        TicketStatus from = ticket.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot move a ticket from %s to %s.".formatted(from, target));
        }
        ticket.setStatus(target);
        if (target == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
        }
        if (target == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        writeStatusHistory(ticket, from, target, actorId, remarks);
    }

    private void writeStatusHistory(Ticket ticket, TicketStatus from, TicketStatus to, UUID actorId, String remarks) {
        statusHistoryRepository.save(TicketStatusHistory.builder()
                .ticketId(ticket.getId())
                .fromStatus(from)
                .toStatus(to)
                .changedByUserId(actorId)
                .remarks(trimOrNull(remarks))
                .build());
    }

    private void writeAssignmentHistory(Ticket ticket, UUID assigneeId, UUID actorId,
                                         TicketAssignmentAction action, String remarks) {
        assignmentHistoryRepository.save(TicketAssignmentHistory.builder()
                .ticketId(ticket.getId())
                .assignedToUserId(assigneeId)
                .assignedByUserId(actorId)
                .action(action)
                .remarks(trimOrNull(remarks))
                .build());
    }

    // ------------------------------------------------------------------ scoping

    private Ticket loadForRead(UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (caller.isSuperAdmin()) {
            // A SUPER_ADMIN's own JWT still carries a (sentinel) company id, so
            // CompanyContext is never truly empty for this role — findById is used
            // specifically because entityManager.find()-by-primary-key bypasses the
            // Hibernate companyFilter (see CompanyFilterAspect's own documented
            // limitation), unlike every derived/JPQL query. Once the ticket's real
            // company is known, rebind CompanyContext to it for the rest of this
            // request/thread so every subsequent company-owned lookup this method's
            // caller makes (sub-resources, directory lookups, saves) is correctly
            // scoped instead of silently filtered against the sentinel.
            Ticket ticket = ticketRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
            CompanyContext.setCompanyId(ticket.getCompanyId());
            return ticket;
        }
        UUID companyId = requireCompany();
        return ticketRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireVisible(Ticket ticket) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (caller.isSuperAdmin() || isCompanyAdmin(caller)) {
            return;
        }
        if (ticket.isRequester(caller.userId()) || ticket.isAssignee(caller.userId())) {
            return;
        }
        UUID ownBranch = ownBranch(caller, ticket.getCompanyId()).orElse(null);
        if (ownBranch != null && ownBranch.equals(ticket.getRelatedBranchId())) {
            return;
        }
        throw new ResourceNotFoundException(ENTITY, ticket.getId());
    }

    /** True when the caller is the ticket's own assignee or a company/super admin — the
     *  bar for seeing internal notes and adding one. */
    private boolean isStaffOn(Ticket ticket) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return caller.isSuperAdmin() || isCompanyAdmin(caller) || ticket.isAssignee(caller.userId());
    }

    private void requireStaffOn(Ticket ticket, AuthenticatedUser caller) {
        if (!(caller.isSuperAdmin() || isCompanyAdmin(caller) || ticket.isAssignee(caller.userId()))) {
            throw new ForbiddenException("Only the assigned agent or a company admin may do this.");
        }
    }

    private UUID requireAssignee(UUID assigneeUserId, UUID companyId) {
        if (assigneeUserId == null) {
            throw new BusinessRuleException("Name who this ticket is assigned to.");
        }
        directory.findUser(assigneeUserId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeUserId));
        return assigneeUserId;
    }

    private Optional<UUID> ownBranch(AuthenticatedUser caller, UUID companyId) {
        Optional<UUID> placement = directory.branchOfUser(caller.userId(), companyId);
        if (placement.isPresent()) {
            return placement;
        }
        return directory.branchManagedBy(caller.userId(), companyId);
    }

    private boolean isCompanyAdmin(AuthenticatedUser caller) {
        return caller.hasRole(Roles.COMPANY_ADMIN);
    }

    // ------------------------------------------------------------------ numbering / helpers

    private String nextTicketNumber(UUID companyId) {
        byte[] companyIdBytes = TimeOrderedUuid.toBytes(companyId);
        sequenceRepository.advance(companyIdBytes);
        long serial = sequenceRepository.nextValue();
        return "TKT-%06d".formatted(serial);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private static String sanitise(String filename) {
        if (filename == null) {
            return "file";
        }
        int dot = filename.lastIndexOf('.');
        String base = dot < 0 ? filename : filename.substring(0, dot);
        return base.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(label + " is required.");
        }
        return value.trim();
    }

    private static String label(TicketStatus status) {
        String[] words = status.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }

    private static String trimOrNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. A ticket belongs to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
