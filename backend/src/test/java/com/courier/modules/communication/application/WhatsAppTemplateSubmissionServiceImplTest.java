package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppTemplateSubmissionServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock CommunicationTemplateRepository templateRepository;
    @Mock CommunicationSettingRepository settingRepository;
    @Mock WhatsAppProvider provider;
    @Mock AuditService auditService;

    private WhatsAppTemplateSubmissionServiceImpl service;
    private CommunicationTemplate template;

    @BeforeEach
    void setUp() {
        service = new WhatsAppTemplateSubmissionServiceImpl(templateRepository, settingRepository, provider,
                auditService, new ObjectMapper());
        CompanyContext.setCompanyId(COMPANY);
        template = CommunicationTemplate.builder().eventType(CommunicationEventType.SHIPMENT_BOOKED)
                .channel(CommunicationChannel.WHATSAPP).templateName("shipment_booked")
                .content("Hi {{customerName}}, your AWB {{trackingNumber}} is booked. Thanks!").build();
        when(templateRepository.findByIdWithinCompany(any(), any())).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(setting("{\"phoneNumberId\":\"111\",\"businessAccountId\":\"WABA9\"}", "tok")));
        when(provider.submitTemplate(any(), any())).thenReturn(
                new WhatsAppProvider.TemplateSubmissionResult("555", "PENDING", "UTILITY"));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private static CommunicationSetting setting(String json, String secret) {
        CommunicationSetting s = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP)
                .enabled(true).configJson(json).build();
        s.setSecret(secret);
        return s;
    }

    @Test
    void submitSendsConvertedBodyAndStoresMetasAnswer() {
        var saved = service.submit(UUID.randomUUID(), null, null);

        ArgumentCaptor<WhatsAppProvider.TemplateSubmission> sub = ArgumentCaptor.forClass(WhatsAppProvider.TemplateSubmission.class);
        ArgumentCaptor<WhatsAppProvider.WhatsAppCredentials> creds = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppCredentials.class);
        verify(provider).submitTemplate(sub.capture(), creds.capture());
        assertThat(sub.getValue().name()).isEqualTo("shipment_booked");
        assertThat(sub.getValue().language()).isEqualTo("en_US");
        assertThat(sub.getValue().category()).isEqualTo("UTILITY");
        assertThat(sub.getValue().bodyText()).isEqualTo("Hi {{1}}, your AWB {{2}} is booked. Thanks!");
        assertThat(sub.getValue().bodyExamples()).containsExactly("Rahul Sharma", "AWB123456789");
        assertThat(creds.getValue().businessAccountId()).isEqualTo("WABA9");

        assertThat(saved.getMetaStatus()).isEqualTo("PENDING");
        assertThat(saved.getMetaTemplateId()).isEqualTo("555");
        assertThat(saved.getMetaLanguage()).isEqualTo("en_US");
        assertThat(saved.getMetaSubmittedAt()).isNotNull();
    }

    @Test
    void submitRefusesANameMetaWouldReject() {
        template.setTemplateName("Shipment Booked - WhatsApp");

        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lowercase");
        verify(provider, never()).submitTemplate(any(), any());
    }

    @Test
    void submitRefusesAnAlreadySubmittedTemplate() {
        template.setMetaStatus("PENDING");

        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already submitted");
    }

    @Test
    void submitRefusesNonWhatsAppAndBadCategoryAndBadLanguage() {
        template.setChannel(CommunicationChannel.SMS);
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only WhatsApp");

        template.setChannel(CommunicationChannel.WHATSAPP);
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), "PROMO", null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Category");
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, "English"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Language");
    }

    @Test
    void submitNeedsBusinessAccountIdAndToken() {
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(setting("{\"phoneNumberId\":\"111\"}", "tok")));
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Business Account ID");

        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not configured");
    }

    @Test
    void refreshStoresStatusAndRejectionReason() {
        template.setMetaStatus("PENDING");
        template.setMetaLanguage("en_US");
        when(provider.fetchTemplateStatus("shipment_booked", "en_US", new WhatsAppProvider.WhatsAppCredentials(
                "111", "WABA9", "tok"))).thenReturn(new WhatsAppProvider.TemplateStatusResult(
                "555", "REJECTED", "UTILITY", "INVALID_FORMAT"));

        var saved = service.refreshStatus(UUID.randomUUID());

        assertThat(saved.getMetaStatus()).isEqualTo("REJECTED");
        assertThat(saved.getMetaStatusDetail()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void refreshOnAHandMadeTemplateAdoptsItsMetaState() {
        when(provider.fetchTemplateStatus(any(), any(), any())).thenReturn(
                new WhatsAppProvider.TemplateStatusResult("9", "APPROVED", "UTILITY", null));

        var saved = service.refreshStatus(UUID.randomUUID());

        assertThat(saved.getMetaStatus()).isEqualTo("APPROVED");
        assertThat(saved.getMetaLanguage()).isEqualTo("en_US");
        assertThat(saved.isApprovedOrUnknownAtMeta()).isTrue();
    }

    @Test
    void refreshExplainsWhenMetaHasNoSuchTemplate() {
        when(provider.fetchTemplateStatus(any(), any(), any())).thenReturn(
                new WhatsAppProvider.TemplateStatusResult(null, "NOT_FOUND", null, null));

        assertThatThrownBy(() -> service.refreshStatus(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no template named");
        assertThat(template.getMetaStatus()).isNull();
    }

    @Test
    void aMetaFailureReachesTheAdminAsAReadableBusinessError() {
        when(provider.submitTemplate(any(), any())).thenThrow(
                new com.courier.modules.communication.application.provider.ProviderSendException(
                        "Meta rejected the template: Template name already exists (code 100)"));
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already exists");
        assertThat(template.getMetaStatus()).isNull();

        when(provider.fetchTemplateStatus(any(), any(), any())).thenThrow(
                new com.courier.modules.communication.application.provider.ProviderSendException("Meta lookup failed: expired token"));
        assertThatThrownBy(() -> service.refreshStatus(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("expired token");
    }

    @Test
    void submitUsesTheTemplatesOwnCategoryLanguageHeaderFooterButtonsAndSamples() {
        template.setCategory("MARKETING");
        template.setLanguage("hi");
        template.setHeaderText("Shipment {{shipmentNumber}}");
        template.setFooterText("Thank you");
        var mapper = new ObjectMapper();
        template.setButtonsJson(TemplateComponentsCodec.writeButtons(mapper, java.util.List.of(
                new com.courier.modules.communication.domain.TemplateButton(
                        com.courier.modules.communication.domain.TemplateButton.Type.URL, "Track",
                        "https://courier.skra.in/track/{{trackingNumber}}", null),
                new com.courier.modules.communication.domain.TemplateButton(
                        com.courier.modules.communication.domain.TemplateButton.Type.PHONE_NUMBER, "Call us", null, "+919123456780"))));
        template.setVariableSamplesJson(TemplateComponentsCodec.writeSamples(mapper,
                java.util.Map.of("customerName", "Asha Patil", "shipmentNumber", "SHP-77")));

        var saved = service.submit(UUID.randomUUID(), null, null);

        ArgumentCaptor<WhatsAppProvider.TemplateSubmission> sub = ArgumentCaptor.forClass(WhatsAppProvider.TemplateSubmission.class);
        verify(provider).submitTemplate(sub.capture(), any());
        var s = sub.getValue();
        assertThat(s.category()).isEqualTo("MARKETING");
        assertThat(s.language()).isEqualTo("hi");
        assertThat(s.bodyExamples()).containsExactly("Asha Patil", "AWB123456789");
        assertThat(s.headerText()).isEqualTo("Shipment {{1}}");
        assertThat(s.headerExample()).isEqualTo("SHP-77");
        assertThat(s.footerText()).isEqualTo("Thank you");
        assertThat(s.buttons()).hasSize(2);
        assertThat(s.buttons().get(0).url()).isEqualTo("https://courier.skra.in/track/{{1}}");
        assertThat(s.buttons().get(0).urlExample()).isEqualTo("https://courier.skra.in/track/AWB123456789");
        assertThat(s.buttons().get(1).phoneNumber()).isEqualTo("+919123456780");
        assertThat(saved.getMetaLanguage()).isEqualTo("hi");
    }
}
