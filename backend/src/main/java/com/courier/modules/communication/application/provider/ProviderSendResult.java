package com.courier.modules.communication.application.provider;

/** What every channel provider hands back — enough to update a {@code CommunicationLog}
 *  row and nothing more (never a raw vendor payload, which might carry PII/secrets a log
 *  shouldn't retain). */
public record ProviderSendResult(String providerMessageId) {
}
