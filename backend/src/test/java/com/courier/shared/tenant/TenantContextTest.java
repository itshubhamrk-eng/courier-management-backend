package com.courier.shared.company;

import com.courier.shared.exception.CompanyIsolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyContextTest {

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("binds and returns the current company")
    void bindsCompany() {
        UUID companyId = UUID.randomUUID();
        CompanyContext.setCompanyId(companyId);

        assertThat(CompanyContext.getCompanyId()).contains(companyId);
        assertThat(CompanyContext.requireCompanyId()).isEqualTo(companyId);
        assertThat(CompanyContext.isSet()).isTrue();
    }

    @Test
    @DisplayName("is empty when nothing is bound")
    void emptyByDefault() {
        assertThat(CompanyContext.getCompanyId()).isEmpty();
        assertThat(CompanyContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("requireCompanyId fails loudly rather than querying across all companies")
    void requireFailsWhenUnbound() {
        assertThatThrownBy(CompanyContext::requireCompanyId)
                .isInstanceOf(CompanyIsolationException.class)
                .hasMessageContaining("No company bound");
    }

    @Test
    @DisplayName("the binding does not leak into other threads")
    void doesNotLeakAcrossThreads() throws Exception {
        // The ThreadLocal is deliberately not inheritable: a background task must
        // never silently pick up the company of whichever request spawned it.
        CompanyContext.setCompanyId(UUID.randomUUID());

        AtomicReference<Boolean> seenInOtherThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            seenInOtherThread.set(CompanyContext.isSet());
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(seenInOtherThread.get()).isFalse();
    }

    @Test
    @DisplayName("clear removes the binding so a pooled thread starts clean")
    void clearRemovesBinding() {
        CompanyContext.setCompanyId(UUID.randomUUID());
        CompanyContext.clear();

        assertThat(CompanyContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("runAs restores the previous company afterwards")
    void runAsRestoresPrevious() {
        UUID original = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        CompanyContext.setCompanyId(original);

        UUID observed = CompanyContext.runAs(other, CompanyContext::requireCompanyId);

        assertThat(observed).isEqualTo(other);
        assertThat(CompanyContext.getCompanyId()).contains(original);
    }

    @Test
    @DisplayName("runAs leaves nothing bound when there was no previous company")
    void runAsClearsWhenNoPrevious() {
        UUID company = UUID.randomUUID();

        CompanyContext.runAs(company, () -> assertThat(CompanyContext.isSet()).isTrue());

        assertThat(CompanyContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("runAs restores the previous company even when the action throws")
    void runAsRestoresOnException() {
        UUID original = UUID.randomUUID();
        CompanyContext.setCompanyId(original);

        assertThatThrownBy(() -> CompanyContext.runAs(UUID.randomUUID(), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(CompanyContext.getCompanyId()).contains(original);
    }

    @Test
    @DisplayName("setting a null company clears rather than storing null")
    void nullClears() {
        CompanyContext.setCompanyId(UUID.randomUUID());
        CompanyContext.setCompanyId(null);

        assertThat(CompanyContext.isSet()).isFalse();
    }
}
