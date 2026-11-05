package com.courier.modules.security.application.scan;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityScanJobTest {

    static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    // defaults: lag 30s, window 10m
    static final Instant TO = NOW.minusSeconds(30);
    static final Instant FROM = TO.minus(Duration.ofMinutes(10));

    @Mock ScanCursorRepository cursorRepository;
    @Mock SecurityIncidentService incidentService;
    ScanRule ruleA;
    ScanRule ruleB;
    SecurityScanJob job;

    @BeforeEach
    void setUp() {
        ruleA = mock(ScanRule.class);
        ruleB = mock(ScanRule.class);
        lenient().when(ruleA.code()).thenReturn(IncidentRule.SESSION_AGENT_CHANGE);
        lenient().when(ruleB.code()).thenReturn(IncidentRule.SESSION_MULTI_IP);
        job = new SecurityScanJob(cursorRepository, List.of(ruleA, ruleB), incidentService,
                new SecurityProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IncidentRequest request(IncidentRule rule) {
        return new IncidentRequest(UUID.randomUUID(), UUID.randomUUID(), rule, Map.of("k", "v"));
    }

    @Test
    void defaultsAreTenMinuteWindowAndThirtySecondLag() {
        SecurityProperties p = new SecurityProperties();
        assertThat(p.getScanWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(p.getScanLag()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void firstRunStoresLaggedCursorAndScansNothing() {
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.empty());

        job.scan();

        ArgumentCaptor<ScanCursor> saved = ArgumentCaptor.forClass(ScanCursor.class);
        verify(cursorRepository).save(saved.capture());
        assertThat(saved.getValue().getLastScannedAt()).isEqualTo(TO);
        verify(ruleA, never()).scan(any(), any());
        verify(ruleB, never()).scan(any(), any());
        verify(incidentService, never()).raise(any());
    }

    @Test
    void firstRunInsertRaceIsSwallowed() {
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.empty());
        when(cursorRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        job.scan();

        verify(ruleA, never()).scan(any(), any());
    }

    @Test
    void laterRunScansSlidingWindowEndingAtLaggedNowAndStoresTo() {
        // cursor far older than the window: the window start must NOT follow it.
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, NOW.minus(Duration.ofHours(5)));
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        IncidentRequest r1 = request(IncidentRule.SESSION_AGENT_CHANGE);
        IncidentRequest r2 = request(IncidentRule.SESSION_MULTI_IP);
        when(ruleA.scan(FROM, TO)).thenReturn(List.of(r1));
        when(ruleB.scan(FROM, TO)).thenReturn(List.of(r2));

        job.scan();

        verify(incidentService).raise(r1);
        verify(incidentService).raise(r2);
        assertThat(cursor.getLastScannedAt()).isEqualTo(TO);
        verify(cursorRepository).save(cursor);
    }

    @Test
    void consecutiveRunsOverlap() {
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, TO.minus(Duration.ofMinutes(2)));
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        when(ruleA.scan(any(), any())).thenReturn(List.of());
        when(ruleB.scan(any(), any())).thenReturn(List.of());

        job.scan();
        SecurityScanJob later = new SecurityScanJob(cursorRepository, List.of(ruleA, ruleB), incidentService,
                new SecurityProperties(), Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
        later.scan();

        verify(ruleA).scan(FROM, TO);
        verify(ruleA).scan(FROM.plusSeconds(120), TO.plusSeconds(120));
    }

    @Test
    void failingRuleDoesNotBlockOthersAndCursorStillAdvances() {
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, FROM);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        when(ruleA.scan(any(), any())).thenThrow(new IllegalStateException("boom"));
        IncidentRequest r2 = request(IncidentRule.SESSION_MULTI_IP);
        when(ruleB.scan(any(), any())).thenReturn(List.of(r2));

        job.scan();

        verify(incidentService).raise(r2);
        assertThat(cursor.getLastScannedAt()).isEqualTo(TO);
        verify(cursorRepository).save(cursor);
    }

    @Test
    void failingRaiseDoesNotStopRemainingRequests() {
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, FROM);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        IncidentRequest bad = request(IncidentRule.SESSION_AGENT_CHANGE);
        IncidentRequest good = request(IncidentRule.SESSION_AGENT_CHANGE);
        when(ruleA.scan(any(), any())).thenReturn(List.of(bad, good));
        when(ruleB.scan(any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("db")).when(incidentService).raise(bad);

        job.scan();

        verify(incidentService).raise(good);
    }

    @Test
    void runsWithNoCompanyBound() {
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, FROM);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        when(ruleA.scan(any(), any())).thenAnswer(inv -> {
            seen.set(CompanyContext.getCompanyId());
            return List.of();
        });
        when(ruleB.scan(any(), any())).thenReturn(List.of());

        // Simulate a polluted shared scheduler thread.
        CompanyContext.runAs(UUID.randomUUID(), (Runnable) job::scan);

        assertThat(seen.get()).isEmpty();
    }
}
