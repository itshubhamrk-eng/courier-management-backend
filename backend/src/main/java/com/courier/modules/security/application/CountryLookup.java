package com.courier.modules.security.application;

import java.net.InetAddress;
import java.util.Optional;

/** Maps a public IP address to an ISO country code. Test seam for {@link GeoCountryService}. */
@FunctionalInterface
public interface CountryLookup {

    /** @return upper- or lower-case ISO alpha-2 code, or empty when unknown. May throw on failure. */
    Optional<String> countryOf(InetAddress address) throws Exception;
}
