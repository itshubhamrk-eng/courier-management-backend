package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.EmailProvider;
import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.shared.domain.TimeOrderedUuid;
import lombok.extern.slf4j.Slf4j;

/** Default Email provider — logs instead of sending, the same posture auth's own
 *  {@code LogOnlyNotificationSender} already takes: no {@code spring.mail.host} is
 *  configured in this dev environment. */
@Slf4j
public class LogOnlyEmailProvider implements EmailProvider {

    @Override
    public ProviderSendResult send(EmailMessage message, EmailIdentity identity) {
        String id = "log-email-" + TimeOrderedUuid.generate();
        log.info("[LogOnlyEmailProvider] to={} from={} <{}> subject='{}' -> {}",
                message.toEmail(), identity.fromName(), identity.fromEmail(), message.subject(), id);
        return new ProviderSendResult(id);
    }
}
