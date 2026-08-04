package com.courier.modules.master.application.command;

import com.courier.modules.master.domain.DistanceUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input to create or update a route. See {@link CountryCommand} for the code/version rule.
 *
 * <p>The branch pair is editable on update — unlike the code — because a company
 * re-pointing a lane at a relocated branch is ordinary, and the pair's own uniqueness
 * check runs on every save.
 */
public record RouteCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        BigDecimal distanceKm,
        DistanceUnit distanceUnit,
        Integer transitDays,
        Integer transitHours,
        String via,
        Long expectedVersion
) {
}
