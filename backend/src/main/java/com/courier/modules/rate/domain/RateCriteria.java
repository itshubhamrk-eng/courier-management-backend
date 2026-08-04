package com.courier.modules.rate.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a rate search. Every field optional; null means "do not constrain".
 *
 * @param routeIds        match any of these routes
 * @param serviceTypeIds  match any of these service types
 * @param packageTypeIds  match any of these package types
 * @param paymentModeIds  match any of these payment modes
 * @param statuses        match any of these statuses
 * @param search          free text over rate code and rate name
 */
public record RateCriteria(
        Set<UUID> routeIds,
        Set<UUID> serviceTypeIds,
        Set<UUID> packageTypeIds,
        Set<UUID> paymentModeIds,
        Set<RateStatus> statuses,
        String search
) {

    public static RateCriteria none() {
        return new RateCriteria(null, null, null, null, null, null);
    }
}
