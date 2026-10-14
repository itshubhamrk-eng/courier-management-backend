package com.courier.modules.communication.domain;

/**
 * One call-to-action button under a WhatsApp message. Only the two kinds that need nothing from
 * the customer: a link and a call. (A quick-reply button needs an inbound webhook to be useful,
 * which this app does not have, so it is not offered.)
 *
 * @param type        {@link Type#URL} or {@link Type#PHONE_NUMBER}
 * @param text        the label, at most 25 characters (Meta's limit)
 * @param url         URL buttons: {@code https://...}, optionally ending in one {@code {{variable}}}
 * @param phoneNumber call buttons: international format, e.g. {@code +919876543210}
 */
public record TemplateButton(Type type, String text, String url, String phoneNumber) {

    public enum Type {
        URL, PHONE_NUMBER
    }
}
