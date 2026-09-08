package com.courier.modules.charge.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a charge search. Every field optional; null means "do not constrain".
 *
 * @param serviceTypeIds match any of these service types
 * @param statuses       match any of these statuses
 * @param search         free text over charge name
 */
public record ChargeCriteria(
        Set<UUID> serviceTypeIds,
        Set<ChargeStatus> statuses,
        String search
) {

    public static ChargeCriteria none() {
        return new ChargeCriteria(null, null, null);
    }
}
