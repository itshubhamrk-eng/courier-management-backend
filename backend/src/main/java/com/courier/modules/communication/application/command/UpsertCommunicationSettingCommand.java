package com.courier.modules.communication.application.command;

import com.courier.modules.communication.domain.CommunicationChannel;

import java.util.Map;

/**
 * @param secret     the plaintext secret (WhatsApp access token / SMS API key). Null means
 *                    "keep the one already stored" — same convention {@code
 *                    CompanyRazorpayConfigRequest.keySecret} already uses. Never returned by
 *                    any read.
 * @param config     non-secret provider config (WhatsApp: phoneNumberId/businessAccountId;
 *                    SMS: apiUrl/senderId; Email: fromName/fromEmail) — full replacement.
 */
public record UpsertCommunicationSettingCommand(
        CommunicationChannel channel,
        boolean enabled,
        String provider,
        Map<String, String> config,
        String secret
) {
}
