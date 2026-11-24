package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.event.AccountCredentialsIssued;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Welcomes a freshly created branch/user account over WhatsApp with its login URL, company code,
 * username, contact number and password.
 *
 * <p>Runs after the creating transaction committed and off the request thread, so a slow or failing
 * Meta call can never delay or roll back creating the account. Deliberately keeps no ledger row:
 * the password must not be written anywhere durable, so a failure is only logged (masked) and the
 * administrator can hand over the credentials or reset the password instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeCredentialsListener {

    private final CommunicationSettingRepository settingRepository;
    private final CommunicationTemplateService templateService;
    private final TemplateRenderer templateRenderer;
    private final WhatsAppProvider whatsAppProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.app-base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Async("communicationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AccountCredentialsIssued event) {
        try {
            CompanyContext.runAs(event.companyId(), () -> send(event));
        } catch (RuntimeException e) {
            log.warn("Welcome message for user {} in company {} not sent — {}", event.userId(),
                    event.companyId(), ReportSendSupport.errorText(e));
        }
    }

    private void send(AccountCredentialsIssued event) {
        if (event.contact() == null || event.contact().isBlank()) {
            log.info("Welcome message for user {} skipped — no mobile number on the account", event.userId());
            return;
        }
        CommunicationSetting setting = settingRepository
                .findByCompanyIdAndChannel(event.companyId(), CommunicationChannel.WHATSAPP)
                .filter(CommunicationSetting::isEnabled).orElse(null);
        if (setting == null) {
            return; // WhatsApp is off for this company: nothing to do, not an error
        }
        CommunicationTemplate template = templateService
                .findActive(event.companyId(), CommunicationEventType.WELCOME_CREDENTIALS, CommunicationChannel.WHATSAPP)
                .orElse(null);
        if (template == null || !template.isApprovedOrUnknownAtMeta()) {
            log.info("Welcome message for user {} skipped — template missing, off, or not approved at Meta",
                    event.userId());
            return;
        }
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());

        whatsAppProvider.send(WhatsAppPayloadBuilder.build(event.contact().trim(), template, templateRenderer,
                variables(event), objectMapper), credentials);
        log.info("Welcome message sent to {} for user {} in company {}", ReportSendSupport.maskPhone(event.contact()),
                event.userId(), event.companyId());
    }

    Map<String, String> variables(AccountCredentialsIssued event) {
        String base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        Map<String, String> v = new LinkedHashMap<>();
        v.put("userName", event.displayName());
        v.put("companyName", event.companyName());
        v.put("loginUrl", base + "/login");
        v.put("companyCode", event.companyCode());
        v.put("loginId", event.loginId());
        v.put("contactNo", event.contact());
        v.put("password", event.password());
        return v;
    }
}
