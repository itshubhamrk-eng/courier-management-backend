package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunicationSettingServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private CommunicationSettingRepository repository;
    @Mock private AuditService auditService;
    @Mock private com.courier.modules.communication.application.provider.WhatsAppProvider whatsAppProvider;
    @Mock private com.courier.modules.communication.domain.CommunicationTemplateRepository templateRepository;

    private CommunicationSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunicationSettingServiceImpl(repository, auditService, new ObjectMapper(), whatsAppProvider,
                templateRepository, new TemplateRenderer("http://x/{trackingNumber}"));
        CompanyContext.setCompanyId(COMPANY);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void list_seedsAllThreeChannelsWhenNoneExistYet() {
        when(repository.findAllByCompanyId(COMPANY)).thenReturn(List.of());

        service.list();

        verify3Saves();
    }

    private void verify3Saves() {
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void upsert_blankSecretKeepsTheOneAlreadyStored() {
        CommunicationSetting existing = CommunicationSetting.builder()
                .channel(CommunicationChannel.WHATSAPP).enabled(false).secret("already-stored-token").build();
        existing.setCompanyId(COMPANY);
        when(repository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(existing));

        CommunicationSetting result = service.upsert(new UpsertCommunicationSettingCommand(
                CommunicationChannel.WHATSAPP, true, "META_CLOUD_API",
                Map.of("phoneNumberId", "12345"), null));

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getSecret()).isEqualTo("already-stored-token");
    }

    @Test
    void upsert_nonBlankSecretRotatesIt() {
        CommunicationSetting existing = CommunicationSetting.builder()
                .channel(CommunicationChannel.WHATSAPP).secret("old-token").build();
        existing.setCompanyId(COMPANY);
        when(repository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(existing));

        CommunicationSetting result = service.upsert(new UpsertCommunicationSettingCommand(
                CommunicationChannel.WHATSAPP, true, "META_CLOUD_API", Map.of(), "new-token"));

        assertThat(result.getSecret()).isEqualTo("new-token");
    }

    @Test
    void testConnection_missingRequiredFieldsFails() {
        when(repository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(CommunicationSetting.builder()
                        .channel(CommunicationChannel.WHATSAPP).enabled(true).build()));

        var result = service.testConnection(CommunicationChannel.WHATSAPP);

        assertThat(result.ok()).isFalse();
    }

    @Test
    void testConnection_disabledChannelFailsRegardlessOfConfig() {
        when(repository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(CommunicationSetting.builder()
                        .channel(CommunicationChannel.WHATSAPP).enabled(false)
                        .secret("token").configJson("{\"phoneNumberId\":\"123\"}").build()));

        var result = service.testConnection(CommunicationChannel.WHATSAPP);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("disabled");
    }

    @org.junit.jupiter.api.Test
    void sendTestMessageSendsNamedTemplateWithStoredCredentials() {
        var setting = com.courier.modules.communication.domain.CommunicationSetting.builder()
                .channel(com.courier.modules.communication.domain.CommunicationChannel.WHATSAPP).enabled(true)
                .configJson("{\"phoneNumberId\":\"123456789012\"}").secret("tok").build();
        setting.setCompanyId(COMPANY);
        when(repository.findByCompanyIdAndChannel(COMPANY,
                com.courier.modules.communication.domain.CommunicationChannel.WHATSAPP))
                .thenReturn(java.util.Optional.of(setting));
        when(whatsAppProvider.send(any(), any())).thenReturn(
                new com.courier.modules.communication.application.provider.ProviderSendResult("wamid.1"));

        var result = service.sendTestMessage("9876543210", "my_tpl", "en");

        org.assertj.core.api.Assertions.assertThat(result.ok()).isTrue();
        org.mockito.Mockito.verify(whatsAppProvider).send(org.mockito.ArgumentMatchers.argThat(
                m -> "my_tpl".equals(m.approvedTemplateName()) && "en".equals(m.languageCode()) && m.templateParameters().isEmpty()), any());
    }

    @org.junit.jupiter.api.Test
    void sendTestMessageRejectsShortNumber() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendTestMessage("123", null, null))
                .isInstanceOf(com.courier.shared.exception.BusinessRuleException.class);
    }
}
