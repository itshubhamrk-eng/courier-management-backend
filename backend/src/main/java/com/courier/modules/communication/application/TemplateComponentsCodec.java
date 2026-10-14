package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.TemplateButton;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/** {@code buttons_json} / {@code variable_samples_json} <-> objects, in one place (same job
 *  {@code CommunicationConfigJson} does for channel settings). A stored value that no longer
 *  parses reads as empty rather than breaking every read of the template. */
@Slf4j
public final class TemplateComponentsCodec {

    private static final TypeReference<List<TemplateButton>> BUTTONS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> SAMPLES = new TypeReference<>() {
    };

    private TemplateComponentsCodec() {
    }

    public static String writeButtons(ObjectMapper mapper, List<TemplateButton> buttons) {
        return write(mapper, buttons == null || buttons.isEmpty() ? null : buttons);
    }

    public static String writeSamples(ObjectMapper mapper, Map<String, String> samples) {
        return write(mapper, samples == null || samples.isEmpty() ? null : samples);
    }

    public static List<TemplateButton> readButtons(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, BUTTONS);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse stored template buttons — treating as none", e);
            return List.of();
        }
    }

    public static Map<String, String> readSamples(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, SAMPLES);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse stored template samples — treating as none", e);
            return Map.of();
        }
    }

    private static String write(ObjectMapper mapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise template components", e);
        }
    }
}
