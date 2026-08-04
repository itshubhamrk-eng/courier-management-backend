package com.courier.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class TimeOrderedUuidTest {

    @Test
    @DisplayName("generates version 7, variant 2 UUIDs")
    void hasCorrectVersionAndVariant() {
        UUID id = TimeOrderedUuid.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("successive ids are non-decreasing, which is the point of the type")
    void isTimeOrdered() {
        // Index locality on the clustered primary key depends on this property.
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(TimeOrderedUuid.generate());
        }

        for (int i = 1; i < ids.size(); i++) {
            long previous = timestampOf(ids.get(i - 1));
            long current = timestampOf(ids.get(i));
            assertThat(current).isGreaterThanOrEqualTo(previous);
        }
    }

    @Test
    @DisplayName("the embedded timestamp is the current time")
    void encodesCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = TimeOrderedUuid.generate();
        long after = System.currentTimeMillis();

        assertThat(timestampOf(id)).isBetween(before, after);
    }

    @Test
    @DisplayName("no collisions under concurrent generation")
    void isUniqueUnderConcurrency() throws Exception {
        int threads = 16;
        int perThread = 2000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<List<UUID>>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                List<UUID> generated = new ArrayList<>(perThread);
                for (int i = 0; i < perThread; i++) {
                    generated.add(TimeOrderedUuid.generate());
                }
                return generated;
            });
        }

        Set<UUID> all = new HashSet<>();
        for (Future<List<UUID>> future : executor.invokeAll(tasks)) {
            all.addAll(future.get());
        }
        executor.shutdown();

        assertThat(all).hasSize(threads * perThread);
    }

    /** Extracts the 48-bit millisecond timestamp from the high bits. */
    private static long timestampOf(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
