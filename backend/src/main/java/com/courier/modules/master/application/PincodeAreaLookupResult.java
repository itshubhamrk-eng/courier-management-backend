package com.courier.modules.master.application;

import com.courier.modules.master.domain.Area;
import com.courier.modules.master.domain.City;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.State;

import java.util.List;

/**
 * A postal-directory match, resolved to a real (possibly newly created) Area and its
 * full ancestor chain, plus how many other post offices shared the same pincode.
 *
 * @param allMatches every post office this pincode's postal record names, each already
 *                   resolved to a real Area the same way {@code area} was (the same list
 *                   {@link PincodeAreaService#syncAreas} would link once the pincode is
 *                   actually saved) — the create form's own preview of what it will get,
 *                   not just the one auto-selected field.
 */
public record PincodeAreaLookupResult(
        Area area, City city, District district, State state, Country country,
        String postOfficeName, int alternateCount,
        List<GeographyAutoResolver.GeographyMatch> allMatches) {
}
