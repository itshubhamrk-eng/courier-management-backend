package com.courier.modules.shipment.api.dto;

/**
 * @param viaRealMethod true when a real service method ran (deliver/assignOutForDelivery/
 *                      cancel) and its money/wallet/POD side effects applied as normal
 * @param warning       non-null only when the raw fallback ran instead — no side effect fired
 */
public record OverrideStatusResponse(ShipmentResponse shipment, boolean viaRealMethod, String warning) {
}
