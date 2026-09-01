package com.courier.modules.communication.application.provider;

/**
 * Generic HTTP SMS gateway abstraction — the brief's own "create provider abstraction... do
 * not hardcode provider" instruction taken literally: {@code GenericHttpSmsProvider} POSTs
 * to whatever {@code apiUrl} a company configures with whatever {@code apiKey}/{@code
 * senderId} they hold, rather than coupling to one named vendor's SDK. {@code
 * LogOnlySmsProvider} is the default when a company hasn't configured one.
 */
public interface SmsProvider {

    ProviderSendResult send(SmsMessage message, SmsCredentials credentials);

    record SmsMessage(String toE164, String body) {
    }

    record SmsCredentials(
            String providerName,
            String apiUrl,
            String apiKey,
            String senderId
    ) {
        public boolean isComplete() {
            return apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }
}
