package com.courier.modules.security.application.scan;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.domain.IncidentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityScanJobTest {

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
        job = new SecurityScanJob(cursorRepository, List.of(ruleA, ruleB), incidentService);
    }

    private static IncidentRequest request(IncidentRule rule) {
        return new IncidentRequest(UUID.randomUUID(), UUID.randomUUID(), rule, Map.of("k", "v"));
    }

    @Test
    void firstRunOnlyStoresCursorAndScansNothing() {
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.empty());

        job.scan();

        ArgumentCaptor<ScanCursor> saved = ArgumentCaptor.forClass(ScanCursor.class);
        verify(cursorRepository).save(saved.capture());
        assertThat(saved.getValue().getLastScannedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        verify(ruleA, never()).scan(any(), any());
        verify(ruleB, never()).scan(any(), any());
        verify(incidentService, never()).raise(any());
    }

    @Test
    void laterRunScansFromCursorRaisesAndAdvancesCursor() {
        Instant cursorAt = Instant.now().minus(Duration.ofMinutes(2));
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, cursorAt);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        IncidentRequest r1 = request(IncidentRule.SESSION_AGENT_CHANGE);
        IncidentRequest r2 = request(IncidentRule.SESSION_MULTI_IP);
        when(ruleA.scan(eq(cursorAt), any())).thenReturn(List.of(r1));
        when(ruleB.scan(eq(cursorAt), any())).thenReturn(List.of(r2));

        job.scan();

        verify(incidentService).raise(r1);
        verify(incidentService).raise(r2);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(ruleA).scan(eq(cursorAt), to.capture());
        assertThat(cursor.getLastScannedAt()).isEqualTo(to.getValue());
        verify(cursorRepository).save(cursor);
    }

    @Test
    void failingRuleDoesNotBlockOthersAndHoldsCursorBack() {
        Instant cursorAt = Instant.now().minus(Duration.ofMinutes(2));
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, cursorAt);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        when(ruleA.scan(any(), any())).thenThrow(new IllegalStateException("boom"));
        IncidentRequest r2 = request(IncidentRule.SESSION_MULTI_IP);
        when(ruleB.scan(any(), any())).thenReturn(List.of(r2));

        job.scan();

        verify(incidentService).raise(r2);
        // The failed rule's window must be retried next run, so the cursor stays put.
        assertThat(cursor.getLastScannedAt()).isEqualTo(cursorAt);
        verify(cursorRepository, never()).save(any());
    }

    @Test
    void failingRaiseDoesNotStopRemainingRequests() {
        Instant cursorAt = Instant.now().minus(Duration.ofMinutes(2));
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, cursorAt);
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
    void staleCursorIsCappedToMaxLookback() {
        Instant ancient = Instant.now().minus(Duration.ofDays(3));
        ScanCursor cursor = new ScanCursor(ScanCursor.SINGLETON_ID, ancient);
        when(cursorRepository.findById(ScanCursor.SINGLETON_ID)).thenReturn(Optional.of(cursor));
        when(ruleA.scan(any(), any())).thenReturn(List.of());
        when(ruleB.scan(any(), any())).thenReturn(List.of());

        job.scan();

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(ruleA).scan(from.capture(), any());
        assertThat(from.getValue()).isAfter(Instant.now().minus(SecurityScanJob.MAX_LOOKBACK.plusMinutes(1)));
    }
}
