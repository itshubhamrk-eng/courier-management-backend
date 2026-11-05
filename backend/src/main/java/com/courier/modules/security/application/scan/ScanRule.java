package com.courier.modules.security.application.scan;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.domain.IncidentRule;

import java.time.Instant;
import java.util.List;

/**
 * One detector run by {@link SecurityScanJob}. Implementations read existing data for the
 * half-open window {@code [from, to)} and return what to raise; they must not raise or hold
 * a transaction open themselves, and must tolerate being re-run over the same window
 * (the job retries a window when any rule failed; {@code raise} dedups per user and rule).
 */
public interface ScanRule {

    IncidentRule code();

    List<IncidentRequest> scan(Instant from, Instant to);
}
