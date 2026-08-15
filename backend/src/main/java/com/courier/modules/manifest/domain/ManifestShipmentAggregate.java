package com.courier.modules.manifest.domain;

import com.courier.modules.shipment.domain.Shipment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Shipment-count/weight/package-count totals for one manifest — the THC Report's
 * per-row and summary-stat numbers. {@code totalWeight} sums {@code Shipment.chargeableWeight},
 * the same weight figure the Booking/Delivery Report summary rows use.
 */
public record ManifestShipmentAggregate(int shipmentCount, BigDecimal totalWeight, int totalPackages) {

    public static final ManifestShipmentAggregate EMPTY =
            new ManifestShipmentAggregate(0, BigDecimal.ZERO, 0);

    public static ManifestShipmentAggregate of(List<Shipment> shipments) {
        BigDecimal weight = shipments.stream().map(Shipment::getChargeableWeight)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int packages = shipments.stream()
                .mapToInt(s -> s.getNumberOfPackages() == null ? 0 : s.getNumberOfPackages()).sum();
        return new ManifestShipmentAggregate(shipments.size(), weight, packages);
    }
}
