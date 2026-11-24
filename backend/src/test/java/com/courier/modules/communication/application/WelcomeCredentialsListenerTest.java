package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.DefaultCommunicationTemplates;
import com.courier.shared.event.AccountCredentialsIssued;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WelcomeCredentialsListenerTest {

    private final UUID companyId = UUID.randomUUID();

    @Mock CommunicationSettingRepository settingRepository;
    @Mock CommunicationTemplateService templateService;
    @Mock WhatsAppProvider whatsAppProvider;

    private WelcomeCredentialsListener listener;
    private CommunicationTemplate template;

    @BeforeEach
    void setUp() {
        listener = new WelcomeCredentialsListener(settingRepository, templateService,
                new TemplateRenderer("http://x/{trackingNumber}"), whatsAppProvider, new ObjectMapper());
        ReflectionTestUtils.setField(listener, "appBaseUrl", "https://app.example.com/");

        var seed = DefaultCommunicationTemplates.WELCOME_CREDENTIALS;
        template = CommunicationTemplate.builder().eventType(seed.eventType()).channel(seed.channel())
                .templateName(seed.templateName()).content(seed.content()).build();
        CommunicationSetting setting = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP)
                .enabled(true).configJson("{\"phoneNumberId\":\"111\",\"businessAccountId\":\"222\"}").build();
        setting.setCompanyId(companyId);
        setting.setSecret("token");
        when(settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(setting));
        when(templateService.findActive(companyId, CommunicationEventType.WELCOME_CREDENTIALS,
                CommunicationChannel.WHATSAPP)).thenReturn(Optional.of(template));
        when(whatsAppProvider.send(any(), any())).thenReturn(new ProviderSendResult("wamid.1"));
    }

    private AccountCredentialsIssued event(String contact) {
        return new AccountCredentialsIssued(companyId, UUID.randomUUID(), "ACME", "Acme Logistics",
                "Ravi", "ravi@acme.local", contact, "S3cret#Pass");
    }

    @Test
    void sendsUrlCompanyCodeUsernameContactAndPasswordInTemplateOrder() {
        listener.on(event("9876543210"));

        ArgumentCaptor<WhatsAppProvider.WhatsAppMessage> sent = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppMessage.class);
        verify(whatsAppProvider).send(sent.capture(), any());
        assertThat(sent.getValue().toE164()).isEqualTo("9876543210");
        assertThat(sent.getValue().approvedTemplateName()).isEqualTo("welcome_credentials");
        assertThat(sent.getValue().templateParameters()).containsExactly(
                "Ravi", "Acme Logistics", "https://app.example.com/login", "ACME",
                "ravi@acme.local", "9876543210", "S3cret#Pass");
    }

    @Test
    void noMobileNumberSendsNothing() {
        listener.on(event(" "));
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void whatsAppDisabledSendsNothing() {
        when(settingRepository.findByCompanyIdAndChannel(eq(companyId), any())).thenReturn(Optional.empty());
        listener.on(event("9876543210"));
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void templateNotApprovedByMetaSendsNothing() {
        template.setMetaStatus("PENDING");
        listener.on(event("9876543210"));
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void aProviderFailureNeverEscapesAndNeverLogsThePassword() {
        when(whatsAppProvider.send(any(), any())).thenThrow(new RuntimeException("boom"));
        listener.on(event("9876543210")); // must not throw: the account is already created
    }

    @Test
    void eventToStringMasksThePassword() {
        assertThat(event("9876543210").toString()).doesNotContain("S3cret#Pass").contains("password=***");
    }
}
