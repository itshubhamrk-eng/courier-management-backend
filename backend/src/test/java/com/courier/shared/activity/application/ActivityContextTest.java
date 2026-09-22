package com.courier.shared.activity.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityContextTest {

    @AfterEach
    void tearDown() {
        ActivityContext.clear();
    }

    @Test
    void nothingSetByDefault() {
        assertThat(ActivityContext.get()).isNull();
    }

    @Test
    void recordChangeIsReadableUntilCleared() {
        ActivityContext.recordChange("Manifest", "Dispatch", "Dispatched manifest MF-1", "Manifest", "abc-123",
                Map.of("status", "CREATED"), Map.of("status", "DISPATCHED"));

        ActivityContext.Entry entry = ActivityContext.get();
        assertThat(entry).isNotNull();
        assertThat(entry.module()).isEqualTo("Manifest");
        assertThat(entry.oldValue()).containsEntry("status", "CREATED");
        assertThat(entry.newValue()).containsEntry("status", "DISPATCHED");

        ActivityContext.clear();
        assertThat(ActivityContext.get()).isNull();
    }
}
