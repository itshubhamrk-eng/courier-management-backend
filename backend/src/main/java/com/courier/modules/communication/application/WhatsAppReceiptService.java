package com.courier.modules.communication.application;

import java.time.Instant;
import java.util.List;

/**
 * Applies the delivery receipts Meta posts to the webhook. No {@code @PreAuthorize}: the caller is
 * the public webhook endpoint, which has already proved the payload came from Meta (HMAC of the
 * whole body), and there is no company context — a receipt is matched on the message id alone.
 */
public interface WhatsAppReceiptService {

    /**
     * @param messageId Meta's message id ({@code wamid...}) — what we stored as {@code providerMessageId}
     * @param status    {@code sent}, {@code delivered}, {@code read} or {@code failed}
     * @param at        when Meta says it happened; null = now
     * @param error     Meta's failure text for {@code failed}, else null
     */
    record Receipt(String messageId, String status, Instant at, String error) {
    }

    /** @return how many receipts matched a message we sent (the rest — other apps' traffic, or old
     *          messages — are ignored without error) */
    int apply(List<Receipt> receipts);
}
