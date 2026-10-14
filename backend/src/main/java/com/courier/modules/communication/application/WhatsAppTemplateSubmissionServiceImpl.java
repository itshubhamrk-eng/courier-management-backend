package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateSubmissionServiceImpl implements WhatsAppTemplateSubmissionService {

    private static final String ENTITY = "CommunicationTemplate";
    private static final String WRITERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final Set<String> CATEGORIES = Set.of("UTILITY", "MARKETING", "AUTHENTICATION");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,3}(_[A-Z]{2})?$");

    private final CommunicationTemplateRepository templateRepository;
    private final CommunicationSettingRepository settingRepository;
    private final WhatsAppProvider whatsAppProvider;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate submit(UUID templateId, String category, String language) {
        UUID companyId = requireCompany();
        CommunicationTemplate template = loadWhatsAppTemplate(templateId, companyId);
        if (template.hasMetaSubmission()) {
            throw new BusinessRuleException("This template was already submitted to Meta (status "
                    + template.getMetaStatus() + "). Use Check status — or change its name or content to "
                    + "submit a new version.");
        }
        String cat = category == null || category.isBlank() ? template.getCategory()
                : TemplateComponentRules.category(category);
        String lang = language == null || language.isBlank() ? template.getLanguage()
                : TemplateComponentRules.language(language);

        MetaTemplateConverter.requireValidName(template.getTemplateName());
        Map<String, String> samples = TemplateSampleValues.merged(
                TemplateComponentsCodec.readSamples(objectMapper, template.getVariableSamplesJson()));
        MetaTemplateConverter.Converted converted = MetaTemplateConverter.convert(template.getContent(), samples);
        MetaTemplateConverter.ConvertedHeader header = MetaTemplateConverter.convertHeader(template.getHeaderText(), samples);
        var buttons = MetaTemplateConverter.convertButtons(
                TemplateComponentsCodec.readButtons(objectMapper, template.getButtonsJson()), samples);
        WhatsAppProvider.WhatsAppCredentials credentials = credentials(companyId);

        WhatsAppProvider.TemplateSubmissionResult result;
        try {
            result = whatsAppProvider.submitTemplate(new WhatsAppProvider.TemplateSubmission(
                    template.getTemplateName(), lang, cat, converted.bodyText(), converted.examples(),
                    header == null ? null : header.text(), header == null ? null : header.example(),
                    template.getFooterText(),
                    buttons.stream().map(b -> new WhatsAppProvider.SubmissionButton(b.type().name(), b.text(),
                            b.url(), b.urlExample(), b.phoneNumber())).toList()), credentials);
        } catch (ProviderSendException e) {
            throw new BusinessRuleException(e.getMessage()); // Meta's own reason, shown to the admin as a 422
        }

        template.setMetaTemplateId(result.metaTemplateId());
        template.setMetaStatus(result.status());
        template.setMetaStatusDetail(null);
        template.setMetaCategory(result.category());
        template.setMetaLanguage(lang);
        template.setMetaSubmittedAt(Instant.now());
        CommunicationTemplate saved = templateRepository.save(template);

        log.info("Template {} ({}) submitted to Meta in company {}: status {}", saved.getId(),
                saved.getTemplateName(), companyId, saved.getMetaStatus());
        auditService.record(AuditAction.COMMUNICATION_TEMPLATE_UPDATED, ENTITY, saved.getId(),
                Map.of("metaSubmitted", true, "metaStatus", String.valueOf(saved.getMetaStatus()),
                        "name", saved.getTemplateName()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CommunicationTemplate refreshStatus(UUID templateId) {
        UUID companyId = requireCompany();
        CommunicationTemplate template = loadWhatsAppTemplate(templateId, companyId);
        String lang = template.getMetaLanguage() == null ? DEFAULT_LANGUAGE : template.getMetaLanguage();

        WhatsAppProvider.TemplateStatusResult result;
        try {
            result = whatsAppProvider.fetchTemplateStatus(template.getTemplateName(), lang, credentials(companyId));
        } catch (ProviderSendException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        if ("NOT_FOUND".equals(result.status())) {
            throw new BusinessRuleException("Meta has no template named \"" + template.getTemplateName()
                    + "\" in language " + lang + ". Submit it first.");
        }
        template.setMetaTemplateId(result.metaTemplateId() != null ? result.metaTemplateId() : template.getMetaTemplateId());
        template.setMetaStatus(result.status());
        template.setMetaStatusDetail(truncate(result.rejectedReason()));
        template.setMetaCategory(result.category() != null ? result.category() : template.getMetaCategory());
        template.setMetaLanguage(lang);
        CommunicationTemplate saved = templateRepository.save(template);
        log.info("Template {} ({}) Meta status in company {}: {}", saved.getId(), saved.getTemplateName(),
                companyId, saved.getMetaStatus());
        return saved;
    }

    private CommunicationTemplate loadWhatsAppTemplate(UUID id, UUID companyId) {
        CommunicationTemplate template = templateRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        if (template.getChannel() != CommunicationChannel.WHATSAPP) {
            throw new BusinessRuleException("Only WhatsApp templates are submitted to Meta.");
        }
        return template;
    }

    private WhatsAppProvider.WhatsAppCredentials credentials(UUID companyId) {
        CommunicationSetting setting = settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .orElseThrow(() -> new BusinessRuleException("WhatsApp is not configured — open Channel Settings "
                        + "and enter the Business Account ID and Access Token first."));
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());
        if (!credentials.hasBusinessAccount()) {
            throw new BusinessRuleException("The WhatsApp Business Account ID and Access Token are required "
                    + "to submit or check a template — set them in Channel Settings.");
        }
        return credentials;
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 500 ? value.substring(0, 500) : value;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Communication templates belong to a company."));
    }
}
