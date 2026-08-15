package com.courier.modules.company.application.geocoding;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The seam between "a branch has no coordinates yet" and whichever geocoder resolves an
 * address to a point — same split {@code FileStoragePort} draws for POD storage. Unlike
 * that port this one never throws: a branch is a real place whether or not a free lookup
 * service can place it on a map today, so a miss is {@link Optional#empty()}, not a
 * refusal, and branch creation proceeds either way.
 */
public interface GeocodingPort {

    /**
     * Best-effort lookup. Returns empty on a miss, a disabled provider, or any failure
     * reaching the geocoder — never throws.
     */
    Optional<Coordinates> geocode(Query query);

    /**
     * Whatever of a branch's address is known, most specific first. All fields optional;
     * the implementation uses as much as it has.
     */
    record Query(String addressLine, String area, String city, String district,
                 String state, String postalCode, String country) {
    }

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
