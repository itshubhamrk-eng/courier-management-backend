package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationSettingServiceImpl implements CommunicationSettingService {

    private static final String ENTITY = "CommunicationSetting";
    private static final String WRITERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";

    private final CommunicationSettingRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.courier.modules.communication.application.provider.WhatsAppProvider whatsAppProvider;

    @Override
    @Transactional
    @PreAuthorize(READERS + " or hasAuthority('NOTIFICATION_READ')")
    public List<CommunicationSetting> list() {
        UUID companyId = requireCompany();
        List<CommunicationSetting> existing = repository.findAllByCompanyId(companyId);
        if (existing.size() == CommunicationChannel.values().length) {
            return existing;
        }
        for (CommunicationChannel channel : CommunicationChannel.values()) {
            if (existing.stream().noneMatch(s -> s.getChannel() == channel)) {
                CommunicationSetting fresh = CommunicationSetting.builder()
                        .channel(channel).enabled(true).build();
                fresh.setCompanyId(companyId);
                repository.save(fresh);
            }
        }
        return repository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(READERS + " or hasAuthority('NOTIFICATION_READ')")
    public CommunicationSetting get(CommunicationChannel channel) {
        UUID companyId = requireCompany();
        return repository.findByCompanyIdAndChannel(companyId, channel).orElseGet(() -> {
            CommunicationSetting fresh = CommunicationSetting.builder()
                    .channel(channel).enabled(true).build();
            fresh.setCompanyId(companyId);
            return repository.save(fresh);
        });
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS + " or hasAuthority('NOTIFICATION_UPDATE')")
    public CommunicationSetting upsert(UpsertCommunicationSettingCommand command) {
        UUID companyId = requireCompany();
        CommunicationSetting setting = repository.findByCompanyIdAndChannel(companyId, command.channel())
                .orElseGet(() -> {
                    CommunicationSetting fresh = CommunicationSetting.builder().channel(command.channel()).build();
                    fresh.setCompanyId(companyId);
                    return fresh;
                });

        setting.setEnabled(command.enabled());
        setting.setProvider(command.provider());
        setting.setConfigJson(writeConfig(command.config()));
        if (command.secret() != null && !command.secret().isBlank()) {
            setting.setSecret(command.secret().trim());
        }

        CommunicationSetting saved = repository.save(setting);
        log.info("Communication setting {} {} in company {} by {}", saved.getChannel(),
                saved.isEnabled() ? "enabled" : "disabled", companyId, currentActor());
        auditService.record(AuditAction.COMMUNICATION_SETTING_UPDATED, ENTITY, saved.getId(),
                Map.of("channel", saved.getChannel().name(), "enabled", saved.isEnabled(),
                        "provider", saved.getProvider() == null ? "" : saved.getProvider(),
                        "secretRotated", command.secret() != null && !command.secret().isBlank()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS + " or hasAuthority('NOTIFICATION_READ')")
    public ConnectionTestResult testConnection(CommunicationChannel channel) {
        CommunicationSetting setting = get(channel);
        if (!setting.isEnabled()) {
            return new ConnectionTestResult(false, channel + " is disabled for this company.");
        }
        return switch (channel) {
            case WHATSAPP -> requireConfig(setting, "phoneNumberId") && setting.hasSecret()
                    ? new ConnectionTestResult(true, "Phone Number ID and Access Token are set.")
                    : new ConnectionTestResult(false, "Phone Number ID and Access Token are required.");
            case SMS -> requireConfig(setting, "apiUrl") && setting.hasSecret()
                    ? new ConnectionTestResult(true, "API URL and API Key are set.")
                    : new ConnectionTestResult(false, "API URL and API Key are required.");
            case EMAIL -> requireConfig(setting, "fromEmail")
                    ? new ConnectionTestResult(true, "From Email is set (SMTP is platform-configured).")
                    : new ConnectionTestResult(false, "From Email is required.");
        };
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(WRITERS + " or hasAuthority('NOTIFICATION_UPDATE')")
    public ConnectionTestResult sendTestMessage(String recipient) {
        if (recipient == null || recipient.replaceAll("\\D", "").length() < 10) {
            throw new BusinessRuleException("Enter a valid WhatsApp number, with country code if it is not Indian.");
        }
        CommunicationSetting setting = get(CommunicationChannel.WHATSAPP);
        if (!setting.isEnabled()) {
            throw new BusinessRuleException("WhatsApp is disabled for this company. Enable it and save first.");
        }
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        var credentials = new com.courier.modules.communication.application.provider.WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());
        if (!credentials.isComplete()) {
            throw new BusinessRuleException("Phone Number ID and Access Token are required. Save them first.");
        }
        try {
            var result = whatsAppProvider.send(
                    new com.courier.modules.communication.application.provider.WhatsAppProvider.WhatsAppMessage(
                            recipient, "hello_world", List.of(), "en_US", null, List.of()),
                    credentials);
            log.info("WhatsApp test message sent in company {} by {}, message id {}", CompanyContext.getCompanyId().orElse(null),
                    currentActor(), result.providerMessageId());
            if (result.providerMessageId() != null && result.providerMessageId().startsWith("log-whatsapp-")) {
                return new ConnectionTestResult(false, "Nothing was sent: this server runs the log-only WhatsApp "
                        + "provider. Set COMMUNICATION_WHATSAPP_ENABLED=true and restart the backend for real sends.");
            }
            return new ConnectionTestResult(true, "Meta accepted the test message"
                    + (result.providerMessageId() == null ? "." : " (id " + result.providerMessageId() + "). Check the phone."));
        } catch (com.courier.modules.communication.application.provider.ProviderSendException e) {
            log.warn("WhatsApp test message failed in company {}: {}", CompanyContext.getCompanyId().orElse(null),
                    e.getMessage());
            return new ConnectionTestResult(false, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunicationSetting> findEnabled(UUID companyId, CommunicationChannel channel) {
        return repository.findByCompanyIdAndChannel(companyId, channel).filter(CommunicationSetting::isEnabled);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyEnabled(UUID companyId) {
        return repository.existsByCompanyIdAndEnabledTrue(companyId);
    }

    private boolean requireConfig(CommunicationSetting setting, String key) {
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        String value = config.get(key);
        return value != null && !value.isBlank();
    }

    private String writeConfig(Map<String, String> config) {
        return CommunicationConfigJson.write(objectMapper, config);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Communication settings belong to a company."));
    }

    private String currentActor() {
        return com.courier.shared.security.SecurityUtils.getCurrentUserId()
                .map(UUID::toString).orElse("system");
    }
}
