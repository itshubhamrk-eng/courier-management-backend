package com.courier.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * JSON conventions for the public API.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                // ISO-8601 strings, not epoch numbers: readable in logs and unambiguous
                // across clients.
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // An unknown field is usually a client on an older contract, not a
                // reason to 400 the whole request.
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .timeZone(TimeZone.getTimeZone("UTC"));
    }
}
