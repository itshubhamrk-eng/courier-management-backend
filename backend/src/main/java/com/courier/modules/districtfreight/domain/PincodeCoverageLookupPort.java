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
}
