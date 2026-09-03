package com.courier.modules.districtfreight.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What District Level Freight's booking-time freight calculation needs to know about a
 * destination pincode: is it on the network at all, does it carry an ODA surcharge, and
 * which {@code District} does it resolve to — the "Destination Pincode -> Pincode
 * Coverage -> Destination District" step of the calculation sequence.
 *
 * <p>Owned by this module; implemented in {@code modules/master}, which walks the
 * existing {@code Pincode -> Area -> City -> District} master-data chain (the same
 * hierarchy the geography masters already maintain — nothing new is stored for this).
 * District Level Freight never sees {@code Pincode}/{@code Area}/{@code City} directly.
 */
public interface PincodeCoverageLookupPort {

    record CoverageRef(UUID pincodeId, String pincodeCode, boolean serviceable, boolean odaApplicable,
                        UUID districtId, String districtCode, String districtName, boolean districtActive) {
    }

    /** {@code pincodeCode} is matched exactly, case-insensitively — empty when no pincode
     *  on file matches at all (not on the network) or its area/city/district chain is
     *  incomplete (a data gap, treated the same as "not resolvable" by the caller). */
    Optional<CoverageRef> findByPincode(String pincodeCode);

    /** Same as {@link #findByPincode}, but resolves District and ODA off one specific Area
     *  the operator picked from that pincode's own Area dropdown (the {@code
     *  master_pincode_areas} link, 0.32.2) rather than the pincode's single legacy {@code
     *  area_id} — a pincode routinely spans several localities/districts, and once the
     *  operator has named exactly which one, that Area's own district/ODA is more accurate
     *  than the pincode-wide flags. Empty when the pincode has no such Area link on file
     *  (not just any area — this exact pincode+area pairing) or its city/district chain is
     *  incomplete. */
    Optional<CoverageRef> findByPincodeAndArea(String pincodeCode, UUID areaId);
}
