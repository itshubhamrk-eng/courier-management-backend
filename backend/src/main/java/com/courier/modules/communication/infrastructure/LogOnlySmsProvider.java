package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.SmsProvider;
import com.courier.shared.domain.TimeOrderedUuid;
import lombok.extern.slf4j.Slf4j;

/** Default SMS provider — logs instead of calling any vendor. No SMS aggregator credential
 *  exists in this dev environment, the same accepted-gap class every other unwired external
 *  integration in this project already carries. */
@Slf4j
public class LogOnlySmsProvider implements SmsProvider {

    @Override
    public ProviderSendResult send(SmsMessage message, SmsCredentials credentials) {
        String id = "log-sms-" + TimeOrderedUuid.generate();
        log.info("[LogOnlySmsProvider] to={} body='{}' -> {}", mask(message.toE164()), message.body(), id);
        return new ProviderSendResult(id);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
