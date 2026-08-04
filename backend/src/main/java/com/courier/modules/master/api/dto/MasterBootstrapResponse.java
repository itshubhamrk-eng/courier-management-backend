package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * What {@code POST /api/v1/master/bootstrap} did.
 *
 * <p>Both maps are keyed by list name — {@code vehicleTypes}, {@code packageTypes},
 * {@code serviceTypes}, {@code paymentModes}, {@code weightSlabs}. A second run reports
 * everything skipped, which is the point: the operation is idempotent and says so rather
 * than pretending it created rows again.
 */
@Schema(name = "MasterBootstrapResponse", description = "Rows created and skipped, per list")
public record MasterBootstrapResponse(
        Map<String, Integer> created,
        Map<String, Integer> skipped
) {
}
