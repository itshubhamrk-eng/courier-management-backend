package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.CreateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpdateCommunicationTemplateCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.modules.communication.domain.DefaultCommunicationTemplates;
import com.courier.modules.communication.domain.ShipmentSnapshot;
import com.courier.modules.communication.domain.TemplateStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationTemplateServiceImpl implements CommunicationTemplateService {

    private static final String ENTITY = "CommunicationTemplate";
    private static final String WRITERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";

    private final CommunicationTemplateRepository repository;
    private final TemplateRenderer templateRenderer;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(READERS)
    public List<CommunicationTemplate> list() {
        UUID companyId = requireCompany();
        seedDefaultsIfEmpty(companyId);
        return repository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public CommunicationTemplate getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate create(CreateCommunicationTemplateCommand command) {
        UUID companyId = requireCompany();
        if (repository.findByCompanyIdAndEventTypeAndChannel(companyId, command.eventType(), command.channel())
                .isPresent()) {
            throw new BusinessRuleException("A template for " + command.eventType() + " + "
                    + command.channel() + " already exists — edit it instead.");
        }
        CommunicationTemplate template = CommunicationTemplate.builder()
                .eventType(command.eventType())
                .channel(command.channel())
                .templateName(requireText(command.templateName(), "Template name"))
                .subject(command.subject())
                .content(requireText(command.content(), "Content"))
                .status(TemplateStatus.ACTIVE)
                .build();
        template.setCompanyId(companyId);
        CommunicationTemplate saved = repository.save(template);

        log.info("Communication template {} ({} + {}) created in company {} by {}",
                saved.getId(), saved.getEventType(), saved.getChannel(), companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_CREATED, ENTITY, saved.getId(),
                Map.of("eventType", saved.getEventType().name(), "channel", saved.getChannel().name()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate update(UUID id, UpdateCommunicationTemplateCommand command) {
        UUID companyId = requireCompany();
        CommunicationTemplate template = loadOrThrow(id, companyId);
        if (command.expectedVersion() != null && !Objects.equals(template.getVersion(), command.expectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(CommunicationTemplate.class, id);
        }

        template.setTemplateName(requireText(command.templateName(), "Template name"));
        template.setSubject(command.subject());
        template.setContent(requireText(command.content(), "Content"));
        template.setStatus(command.status() == null ? template.getStatus() : command.status());

        CommunicationTemplate saved = repository.save(template);
        log.info("Communication template {} updated in company {} by {}", saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_UPDATED, ENTITY, saved.getId(),
                Map.of("status", saved.getStatus().name()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public RenderedPreview preview(UUID id) {
        CommunicationTemplate template = loadOrThrow(id, requireCompany());
        ShipmentSnapshot sample = sampleShipment();
        String recipientName = template.getEventType() == CommunicationEventType.OUT_FOR_DELIVERY
                || template.getEventType() == CommunicationEventType.SHIPMENT_DELIVERED
                || template.getEventType() == CommunicationEventType.SHIPMENT_RECEIVED
                ? sample.receiver().name() : sample.sender().name();
        String subject = template.getSubject() == null ? null
                : templateRenderer.render(template.getSubject(), sample, template.getEventType(), recipientName);
        String content = templateRenderer.render(template.getContent(), sample, template.getEventType(),
                recipientName);
        return new RenderedPreview(subject, content);
    }

    @Override
    @Transactional
    public Optional<CommunicationTemplate> findActive(UUID companyId, CommunicationEventType eventType,
                                                        CommunicationChannel channel) {
        seedDefaultsIfEmpty(companyId);
        return repository.findByCompanyIdAndEventTypeAndChannel(companyId, eventType, channel)
                .filter(CommunicationTemplate::isActive);
    }

    private void seedDefaultsIfEmpty(UUID companyId) {
        if (repository.countByCompanyId(companyId) > 0) {
            return;
        }
        for (var seed : DefaultCommunicationTemplates.all()) {
            CommunicationTemplate template = CommunicationTemplate.builder()
                    .eventType(seed.eventType())
                    .channel(seed.channel())
                    .templateName(seed.templateName())
                    .subject(seed.subject())
                    .content(seed.content())
                    .status(TemplateStatus.ACTIVE)
                    .build();
            template.setCompanyId(companyId);
            try {
                repository.save(template);
            } catch (DataIntegrityViolationException e) {
                // A concurrent request seeded the same company first — the unique
                // constraint is the real guard, this is just a quiet loser.
                log.debug("Default template {}+{} already seeded for company {}",
                        seed.eventType(), seed.channel(), companyId);
            }
        }
    }

    private static ShipmentSnapshot sampleShipment() {
        ShipmentSnapshot.Party sender = new ShipmentSnapshot.Party(
                "Rahul Sharma", "+919876543210", null, "rahul@example.com", true, true, true);
        ShipmentSnapshot.Party receiver = new ShipmentSnapshot.Party(
                "Priya Verma", "+919812345678", null, "priya@example.com", true, true, true);
        return new ShipmentSnapshot(
                UUID.randomUUID(), "SHP-SAMPLE-001", "AWB123456789", "Your Company",
                sender, receiver, "Pune Hub", "Mumbai Hub",
                new BigDecimal("450.00"), LocalDate.now().plusDays(2),
                "https://example.com/pod/sample.jpg");
    }

    private CommunicationTemplate loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required.");
        }
        return value.trim();
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Communication templates belong to a company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }
}
