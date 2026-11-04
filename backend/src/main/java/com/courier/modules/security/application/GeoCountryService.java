package com.courier.modules.security.application;

import com.courier.modules.security.domain.GeoVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;

/**
 * Fail-open IP to country check. Anything that is not a clearly public, resolvable
 * address yields {@link GeoVerdict#UNKNOWN}; this class never throws.
 */
@Slf4j
@Service
public class GeoCountryService {

    private static final int MAX_LENGTH = 45;

    private final SecurityProperties properties;
    private final CountryLookup lookup;

    public GeoCountryService(SecurityProperties properties, CountryLookup lookup) {
        this.properties = properties;
        this.lookup = lookup;
    }

    public GeoVerdict check(String ip) {
        Optional<String> country = countryOf(ip);
        if (country.isEmpty()) {
            return GeoVerdict.UNKNOWN;
        }
        String allowed = properties.getGeo().getAllowedCountry();
        return allowed != null && allowed.trim().equalsIgnoreCase(country.get())
                ? GeoVerdict.ALLOWED
                : GeoVerdict.FOREIGN;
    }

    /** Upper-case ISO country code, or empty when disabled, non-public, or unresolvable. */
    public Optional<String> countryOf(String ip) {
        if (!properties.getGeo().isEnabled() || lookup == null) {
            return Optional.empty();
        }
        try {
            InetAddress address = parseLiteral(ip);
            if (address == null || !isPublic(address)) {
                return Optional.empty();
            }
            return lookup.countryOf(address)
                    .map(code -> code.trim().toUpperCase(Locale.ROOT))
                    .filter(code -> !code.isEmpty());
        } catch (Exception e) {
            log.debug("Geo lookup failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique-local fc00::/7 is not reported as site-local by the JDK.
        return !(bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC);
    }

    /**
     * Parses an IPv4/IPv6 literal only; anything else returns null and never triggers DNS.
     * IPv4-mapped IPv6 ({@code ::ffff:a.b.c.d}) comes back as a plain IPv4 address.
     */
    private static InetAddress parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        try {
            if (value.indexOf(':') < 0) {
                byte[] v4 = parseIpv4(value);
                return v4 == null ? null : InetAddress.getByAddress(v4);
            }
            // Shape check: hex digits, ':' and a trailing embedded IPv4 only. A string
            // containing ':' is always parsed as an IPv6 literal by the JDK, never as a hostname.
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c != ':' && c != '.' && Character.digit(c, 16) < 0) {
                    return null;
                }
            }
            if (value.indexOf('.') >= 0) {
                if (parseIpv4(value.substring(value.lastIndexOf(':') + 1)) == null
                        || value.indexOf('.') < value.lastIndexOf(':')) {
                    return null;
                }
            }
            // The JDK returns an Inet4Address for IPv4-mapped IPv6 literals.
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            int n = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                n = n * 10 + (c - '0');
            }
            if (n > 255) {
                return null;
            }
            out[i] = (byte) n;
        }
        return out;
    }
}
