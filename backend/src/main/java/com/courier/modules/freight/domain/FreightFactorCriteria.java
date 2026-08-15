package com.courier.modules.freight.domain;

import java.util.Set;

/**
 * Filter criteria for a freight factor search. Every field optional; null means
 * "do not constrain".
 *
 * @param statuses match any of these statuses
 */
public record FreightFactorCriteria(Set<FreightFactorStatus> statuses) {

    public static FreightFactorCriteria none() {
        return new FreightFactorCriteria(null);
    }
}
