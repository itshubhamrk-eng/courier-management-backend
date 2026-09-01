package com.courier.modules.communication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/** {@code CommunicationSetting.configJson} <-> {@code Map<String,String>}, shared by
 *  {@code CommunicationSettingServiceImpl} (writes it) and {@code CommunicationSendServiceImpl}
 *  (reads it to build provider credentials) so the encoding lives in exactly one place. */
@Slf4j
public final class CommunicationConfigJson {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private CommunicationConfigJson() {
    }

    public static String write(ObjectMapper objectMapper, Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise communication setting config", e);
        }
    }

    public static Map<String, String> read(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse stored communication setting config — treating as empty", e);
            return Map.of();
        }
    }
}
