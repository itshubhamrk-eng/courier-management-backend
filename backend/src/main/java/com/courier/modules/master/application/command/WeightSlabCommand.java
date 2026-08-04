package com.courier.modules.master.application.command;

import com.courier.modules.master.domain.WeightUnit;

import java.math.BigDecimal;

/**
 * Input to create or update a weight slab. See {@link CountryCommand} for the code/version rule.
 *
 * <p>The band is half-open, {@code [minWeight, maxWeight)}.
 */
public record WeightSlabCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        BigDecimal minWeight,
        BigDecimal maxWeight,
        WeightUnit weightUnit,
        Long expectedVersion
) {
}
