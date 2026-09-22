package com.courier.shared.activity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Masks known-sensitive keys before anything is serialised into {@code activity_logs}.
 *
 * <p>Applied to every old/new value map and to the best-effort request-body snapshot
 * {@code ActivityLoggingFilter} captures for write requests. Matching is by key name,
 * case-insensitively and regardless of nesting depth — the caller of a JSON body decides
 * its field names, not this masker, so depth-first substring matching is the only rule
 * that cannot be defeated by wrapping a secret one level deeper.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class SensitiveDataMasker {

    private static final String MASK = "***MASKED***";

    /** Substring match against the lower-cased key, so `newPassword`, `otpCode`,
     *  `access_token` etc. are all caught without enumerating every spelling. */
    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
            "password", "token", "secret", "otp", "cvv", "pin", "authorization",
            "apikey", "api_key", "cardnumber", "card_number", "signature", "credential");

    private final ObjectMapper objectMapper;

    /** Masks a plain map (the shape {@code ActivityContext} old/new values arrive in). */
    public Map<String, Object> mask(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Map<String, Object> masked = new java.util.LinkedHashMap<>();
        value.forEach((k, v) -> masked.put(k, isSensitiveKey(k) ? MASK : maskNested(v)));
        return masked;
    }

    /** Masks a JSON object captured verbatim off the wire (the request-body snapshot). */
    public String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            var node = objectMapper.readTree(json);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Could not parse captured body for masking; discarding it instead of risking a leak");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskNested(Object v) {
        if (v instanceof Map<?, ?> nested) {
            return mask((Map<String, Object>) nested);
        }
        return v;
    }

    private void maskNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.fieldNames().forEachRemaining(field -> {
                if (isSensitiveKey(field)) {
                    obj.put(field, MASK);
                } else {
                    maskNode(obj.get(field));
                }
            });
        } else if (node != null && node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return SENSITIVE_FRAGMENTS.stream().anyMatch(lower::contains);
    }
}
