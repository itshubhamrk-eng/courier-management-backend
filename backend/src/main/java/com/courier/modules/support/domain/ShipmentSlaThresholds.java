package com.courier.modules.support.domain;

/**
 * A company's configured hour thresholds for each {@link ShipmentSlaStage}, read from
 * {@code company_settings} by {@link TicketDirectoryPort#shipmentSlaSettings}.
 */
public record ShipmentSlaThresholds(
        int bookingToLoadingSheetHours,
        int loadingSheetToThcHours,
        int thcToInscanHours,
        int inscanToDrsHours,
        int drsToDeliveryHours
) {
}
