package com.courier.modules.crossing.domain;

/**
 * Lifecycle of a {@link CrossingDetail}. {@code PENDING} -&gt; {@code IN_TRANSIT} ->
 * {@code COMPLETED}, or {@code PENDING}/{@code IN_TRANSIT} -&gt; {@code CANCELLED}.
 * Terminal once {@code COMPLETED} or {@code CANCELLED}.
 */
public enum CrossingStatus {
    PENDING,
    IN_TRANSIT,
    COMPLETED,
    CANCELLED
}
