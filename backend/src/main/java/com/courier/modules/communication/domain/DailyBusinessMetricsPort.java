package com.courier.modules.communication.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What the daily summary needs to know about a company's business, owned by the consumer
 * ({@code communication}) and implemented where the data lives ({@code dashboard}, which
 * already reads both shipments and wallet) — same arrangement as {@link ShipmentDirectoryPort}.
 * Called from a scheduler thread with no authenticated caller, so implementations go straight
 * to repositories with an explicit company id, never through a {@code @PreAuthorize} service.
 */
public interface DailyBusinessMetricsPort {

    /**
     * @param day  the business day (a calendar date in the company's reporting zone)
     * @param from inclusive start of that day as an instant
     * @param to   exclusive end of that day as an instant
     */
    DailyBusinessMetrics metricsFor(UUID companyId, LocalDate day, Instant from, Instant to);
}
