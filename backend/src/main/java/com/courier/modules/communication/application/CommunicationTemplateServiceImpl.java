package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.CreateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpdateCommunicationTemplateCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.modules.communication.domain.DefaultCommunicationTemplates;
import com.courier.modules.communication.domain.ShipmentSnapshot;
import com.courier.modules.communication.domain.StarterTemplates;
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional
    @PreAuthorize(READERS)
    public List<CommunicationTemplate> list() {
        UUID companyId = requireCompany();
        seedMissingDefaults(companyId);
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

        String newName = requireText(command.templateName(), "Template name");
        String newContent = requireText(command.content(), "Content");

        // Tri-state WhatsApp parts: null = leave as stored, blank/empty = clear, else replace.
        String newCategory = command.category() == null ? template.getCategory()
                : TemplateComponentRules.category(command.category());
        String newLanguage = command.language() == null ? template.getLanguage()
                : TemplateComponentRules.language(command.language());
        String newHeader = command.headerText() == null ? template.getHeaderText()
                : TemplateComponentRules.header(command.headerText());
        String newFooter = command.footerText() == null ? template.getFooterText()
                : TemplateComponentRules.footer(command.footerText());
        String newButtonsJson = command.buttons() == null ? template.getButtonsJson()
                : TemplateComponentsCodec.writeButtons(objectMapper, TemplateComponentRules.buttons(command.buttons()));
        String newSamplesJson = command.variableSamples() == null ? template.getVariableSamplesJson()
                : TemplateComponentsCodec.writeSamples(objectMapper, TemplateComponentRules.samples(command.variableSamples()));

        if (template.getChannel() != CommunicationChannel.WHATSAPP
                && (command.headerText() != null && !command.headerText().isBlank()
                || command.footerText() != null && !command.footerText().isBlank()
                || command.buttons() != null && !command.buttons().isEmpty())) {
            throw new BusinessRuleException("Header, footer and buttons are WhatsApp-only.");
        }

        if (template.hasMetaSubmission() && (!newName.equals(template.getTemplateName())
                || !newContent.equals(template.getContent())
                || !newCategory.equals(template.getCategory())
                || !newLanguage.equals(template.getLanguage())
                || !Objects.equals(newHeader, template.getHeaderText())
                || !Objects.equals(newFooter, template.getFooterText())
                || !Objects.equals(newButtonsJson, template.getButtonsJson()))) {
            // The Meta record describes the old template; keeping its status would let a changed
            // template look approved. Force a fresh submission instead. (Samples only feed Meta's
            // examples, they are not part of what gets approved, so changing them keeps it.)
            template.clearMetaSubmission();
        }
        template.setTemplateName(newName);
        template.setSubject(command.subject());
        template.setContent(newContent);
        template.setCategory(newCategory);
        template.setLanguage(newLanguage);
        template.setHeaderText(newHeader);
        template.setFooterText(newFooter);
        template.setButtonsJson(newButtonsJson);
        template.setVariableSamplesJson(newSamplesJson);
        template.setStatus(command.status() == null ? template.getStatus() : command.status());

        CommunicationTemplate saved = repository.save(template);
        log.info("Communication template {} updated in company {} by {}", saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_UPDATED, ENTITY, saved.getId(),
                Map.of("status", saved.getStatus().name()));
        return saved;
    }

    @Override
    @PreAuthorize(READERS)
    public StarterTemplate starter(CommunicationEventType eventType, CommunicationChannel channel) {
        if (eventType == null || channel == null) {
            throw new BusinessRuleException("Event and channel are required.");
        }
        var seed = StarterTemplates.starter(eventType, channel);
        return new StarterTemplate(seed.templateName(), seed.subject(), seed.content(),
                StarterTemplates.isSeeded(eventType, channel));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate resetToDefault(UUID id) {
        UUID companyId = requireCompany();
        CommunicationTemplate template = loadOrThrow(id, companyId);
        var seed = StarterTemplates.starter(template.getEventType(), template.getChannel());

        template.clearMetaSubmission();
        template.setTemplateName(seed.templateName());
        template.setSubject(seed.subject());
        template.setContent(seed.content());
        template.setCategory("UTILITY");
        template.setLanguage("en_US");
        template.setHeaderText(null);
        template.setFooterText(null);
        template.setButtonsJson(null);
        template.setVariableSamplesJson(null);
        CommunicationTemplate saved = repository.save(template);

        log.info("Communication template {} ({} + {}) reset to its default in company {} by {}", saved.getId(),
                saved.getEventType(), saved.getChannel(), companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_UPDATED, ENTITY, saved.getId(),
                Map.of("resetToDefault", true, "status", saved.getStatus().name()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate setStatus(UUID id, TemplateStatus status) {
        if (status == null) {
            throw new BusinessRuleException("Status is required.");
        }
        UUID companyId = requireCompany();
        CommunicationTemplate template = loadOrThrow(id, companyId);
        if (template.getStatus() == status) {
            return template;
        }
        template.setStatus(status);
        CommunicationTemplate saved = repository.save(template);
        log.info("Communication template {} ({} + {}) switched {} in company {} by {}", saved.getId(),
                saved.getEventType(), saved.getChannel(), status, companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_UPDATED, ENTITY, saved.getId(),
                Map.of("status", status.name(), "statusSwitch", true));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public RenderedPreview preview(UUID id) {
        CommunicationTemplate template = loadOrThrow(id, requireCompany());
        if (template.getEventType() == CommunicationEventType.DAILY_BUSINESS_SUMMARY) {
            return new RenderedPreview(null,
                    templateRenderer.renderWith(template.getContent(), TemplateSampleValues.all()));
        }
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
        seedMissingDefaults(companyId);
        return repository.findByCompanyIdAndEventTypeAndChannel(companyId, eventType, channel)
                .filter(CommunicationTemplate::isActive);
    }

    /** Inserts every default the company doesn't have yet. Additive on purpose: a company seeded
     *  before a default existed (the daily summary arrived after the first twelve) still gets it,
     *  and an edited or switched-off template is never touched. */
    private void seedMissingDefaults(UUID companyId) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (CommunicationTemplate existing : repository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(companyId)) {
            have.add(existing.getEventType() + "|" + existing.getChannel());
        }
        for (var seed : DefaultCommunicationTemplates.all()) {
            if (have.contains(seed.eventType() + "|" + seed.channel())) {
                continue;
            }
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
