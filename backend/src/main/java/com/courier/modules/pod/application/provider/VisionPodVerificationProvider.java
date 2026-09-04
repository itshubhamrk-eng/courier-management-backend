package com.courier.modules.pod.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * A real vision-AI scorer — unlike {@link HeuristicPodVerificationProvider}, this one actually
 * looks at the photo's content (does it plausibly show a delivered parcel / a signature / a
 * doorstep handoff), not just its pixel statistics. Written directly against the vendor's
 * Messages API rather than pulling in an SDK, the same "surface used here is one endpoint,
 * not worth a transitive dependency tree" call {@code RazorpayPaymentGateway} makes for its
 * own vendor.
 *
 * <p>Active only when {@code pod.ai.provider=vision}. A blank {@link PodAiVisionProperties#getApiKey()}
 * or any call failure (timeout, non-2xx, unparseable response) throws
 * {@link PodProviderUnavailableException} — {@code PodVerificationServiceImpl} already treats
 * that as "route to manual review", so no new error-handling path is needed there. This
 * provider never lets a vendor failure silently look like a PASS.
 *
 * <p>The ground-truth hard-fail rules (AWB/QR mismatch, duplicate photo hash) are this
 * module's own policy, not something to trust a vendor's own arithmetic for — applied via
 * {@link PodGroundTruthRules} on top of the vendor's raw score, identically to how
 * {@link HeuristicPodVerificationProvider} applies them.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pod.ai", name = "provider", havingValue = "vision")
public class VisionPodVerificationProvider implements PodVerificationProvider {

    private static final String SYSTEM_PROMPT = """
            You are verifying a courier proof-of-delivery (POD) photo. Look at the image and \
            respond with ONLY a single JSON object (no markdown fences, no other text) with \
            exactly these fields:
            {
              "score": <integer 0-100, your own confidence this is a genuine delivery POD photo>,
              "reasons": [<short strings, most significant first, explaining the score>],
              "signatureDetected": <boolean, is a legible signature or handwritten mark visible>,
              "imageQuality": "GOOD" | "FAIR" | "POOR",
              "looksLikeGenuineDelivery": <boolean, does this plausibly show a real parcel/\
            delivery/signature capture, as opposed to an unrelated photo, screenshot, or \
            document>,
              "detectedReceiverName": <string or null, a name visible in the image, if any>,
              "detectedAwb": <string or null, an AWB/tracking number visible in the image, if any>
            }
            Score low (below 40) if the image does not plausibly show a real delivery capture \
            at all — an unrelated photo, a random document, a screenshot, a blank/mostly-empty \
            image. Score high only when the image genuinely looks like a delivery photo with \
            clear evidence of a handoff (parcel, doorstep, signature, or receiver).""";

    private final PodAiVisionProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public VisionPodVerificationProvider(PodAiVisionProperties properties, ObjectMapper objectMapper,
                                          RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getApiKey() == null ? "" : properties.getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String providerName() {
        return "vision-ai";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public PodAnalysisResult analyze(PodAnalysisRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new PodProviderUnavailableException(
                    "Vision AI provider is not configured — set POD_AI_VISION_API_KEY.");
        }

        JsonNode vendorResult = callVendor(request);

        int score = clamp(vendorResult.path("score").asInt(0));
        List<String> reasons = new ArrayList<>();
        vendorResult.path("reasons").forEach(node -> reasons.add(node.asText()));
        boolean signatureDetected = vendorResult.path("signatureDetected").asBoolean(false);
        String imageQuality = vendorResult.path("imageQuality").asText("FAIR");
        boolean looksGenuine = vendorResult.path("looksLikeGenuineDelivery").asBoolean(true);
        String detectedReceiverName = textOrNull(vendorResult, "detectedReceiverName");
        String detectedAwb = textOrNull(vendorResult, "detectedAwb");

        if (!looksGenuine && reasons.isEmpty()) {
            reasons.add("The image does not plausibly show a genuine delivery/POD capture.");
        }

        PodGroundTruthRules.Outcome groundTruth = PodGroundTruthRules.applyHardFailRules(score, reasons, request);

        String detectedDate = request.deliveryDateTime() == null ? null : request.deliveryDateTime().toString();

        return new PodAnalysisResult(groundTruth.score(), List.copyOf(reasons), signatureDetected, imageQuality,
                detectedReceiverName, detectedAwb, detectedDate, false, groundTruth.mustReview());
    }

    private JsonNode callVendor(PodAnalysisRequest request) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", 1024,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64",
                                        "media_type", contentTypeOrDefault(request.photoContentType()),
                                        "data", Base64.getEncoder().encodeToString(request.photoBytes()))),
                                Map.of("type", "text", "text", "Verify this proof-of-delivery photo.")))));

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/messages")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Vision AI provider call failed — routing to manual review: {}", e.getMessage());
            throw new PodProviderUnavailableException("Vision AI provider call failed.", e);
        }

        String text = extractText(response);
        if (text == null) {
            throw new PodProviderUnavailableException("Vision AI provider returned no usable content.");
        }
        try {
            return objectMapper.readTree(stripMarkdownFence(text));
        } catch (Exception e) {
            log.warn("Vision AI provider response was not valid JSON — routing to manual review");
            throw new PodProviderUnavailableException("Vision AI provider returned an unparseable response.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object content = response.get("content");
        if (!(content instanceof List<?> blocks) || blocks.isEmpty()) {
            return null;
        }
        for (Object block : blocks) {
            if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                Object text = map.get("text");
                if (text != null) {
                    return text.toString();
                }
            }
        }
        return null;
    }

    private static String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private static String contentTypeOrDefault(String contentType) {
        return contentType == null || contentType.isBlank() ? "image/jpeg" : contentType;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
