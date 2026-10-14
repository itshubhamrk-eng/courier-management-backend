package com.courier.modules.communication.api;

import com.courier.modules.communication.application.WhatsAppReceiptService;
import com.courier.modules.communication.application.WhatsAppReceiptService.Receipt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Meta's WhatsApp webhook: the one thing that turns "Sent" into "Delivered" and "Read".
 *
 * <p>Public by necessity — Meta calls it with no bearer token — so it is listed in
 * {@code SecurityConfig.PUBLIC_ENDPOINTS} and trusts nothing it is told except by proof:
 * <ul>
 *   <li><b>GET</b> is Meta's one-off ownership check when the URL is saved in the app dashboard. It
 *       echoes {@code hub.challenge} only when {@code hub.verify_token} equals the token this
 *       deployment was given ({@code COMMUNICATION_WHATSAPP_WEBHOOK_VERIFY_TOKEN}, compared in
 *       constant time).</li>
 *   <li><b>POST</b> carries receipts. The whole body is HMAC-SHA256 signed with the Meta app's secret
 *       ({@code COMMUNICATION_WHATSAPP_APP_SECRET}) in {@code X-Hub-Signature-256}. No secret configured,
 *       no signature or a wrong one all end the same way: refused, nothing recorded.</li>
 * </ul>
 * Once a payload is authentic it is always answered 200 — Meta retries anything else for days, and a
 * receipt for a message this deployment never sent (another environment sharing the app) is not an
 * error. One Meta app serves the whole deployment, so the secret and token are platform-level, not
 * per-company like the access token.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@RequiredArgsConstructor
@Hidden
public class WhatsAppWebhookController {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String PREFIX = "sha256=";
    private static final String HMAC = "HmacSHA256";

    private final WhatsAppReceiptService receiptService;
    private final ObjectMapper objectMapper;

    @Value("${app.communication.whatsapp.webhook.verify-token:}")
    private String verifyToken = "";

    @Value("${app.communication.whatsapp.webhook.app-secret:}")
    private String appSecret = "";

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (isBlank(verifyToken)) {
            log.warn("WhatsApp webhook verification attempted but COMMUNICATION_WHATSAPP_WEBHOOK_VERIFY_TOKEN is not set");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if ("subscribe".equals(mode) && token != null && constantTimeEquals(verifyToken, token) && challenge != null) {
            log.info("WhatsApp webhook verified by Meta");
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        if (isBlank(appSecret)) {
            log.warn("WhatsApp webhook called but COMMUNICATION_WHATSAPP_APP_SECRET is not set — refusing");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (isBlank(signature)) {
            return ResponseEntity.badRequest().build();
        }
        if (!signatureMatches(rawBody, signature)) {
            log.warn("WhatsApp webhook signature did not match — refusing payload");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            log.warn("WhatsApp webhook body was not valid JSON despite a valid signature");
            return ResponseEntity.badRequest().build();
        }

        List<Receipt> receipts = parseReceipts(root);
        if (!receipts.isEmpty()) {
            try {
                int matched = receiptService.apply(receipts);
                log.info("WhatsApp webhook: {} receipt(s), {} matched a message we sent", receipts.size(), matched);
            } catch (RuntimeException e) {
                log.error("WhatsApp webhook: applying receipts failed", e);
                // 5xx so Meta retries — a transient DB error must not drop a receipt silently.
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Pulls {@code entry[].changes[].value.statuses[]} out of Meta's envelope; inbound customer
     *  messages and every other field are not this endpoint's business. */
    static List<Receipt> parseReceipts(JsonNode root) {
        List<Receipt> out = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode status : change.path("value").path("statuses")) {
                    String id = status.path("id").asText(null);
                    String state = status.path("status").asText(null);
                    if (id == null || state == null) {
                        continue;
                    }
                    out.add(new Receipt(id, state, epoch(status.path("timestamp").asText(null)), errorText(status.path("errors"))));
                }
            }
        }
        return out;
    }

    private static Instant epoch(String seconds) {
        try {
            return seconds == null ? null : Instant.ofEpochSecond(Long.parseLong(seconds.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String errorText(JsonNode errors) {
        if (!errors.isArray() || errors.isEmpty()) {
            return null;
        }
        JsonNode first = errors.get(0);
        String title = first.path("title").asText("");
        String message = first.path("message").asText("");
        String code = first.path("code").asText("");
        String text = !title.isBlank() ? title : message;
        if (!title.isBlank() && !message.isBlank() && !message.equals(title)) {
            text = title + " — " + message;
        }
        return text.isBlank() ? (code.isBlank() ? null : "code " + code) : text + (code.isBlank() ? "" : " (code " + code + ")");
    }

    private boolean signatureMatches(byte[] body, String header) {
        String provided = header.startsWith(PREFIX) ? header.substring(PREFIX.length()) : header;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC));
            String expected = HexFormat.of().formatHex(mac.doFinal(body));
            return constantTimeEquals(expected, provided.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
