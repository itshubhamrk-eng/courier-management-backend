package com.courier.modules.security.application;

import com.courier.modules.security.domain.GeoVerdict;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeoCountryServiceTest {

    private final List<InetAddress> seen = new ArrayList<>();

    private GeoCountryService service(boolean enabled, CountryLookup lookup) {
        SecurityProperties props = new SecurityProperties();
        props.getGeo().setEnabled(enabled);
        return new GeoCountryService(props, lookup);
    }

    private CountryLookup returning(String country) {
        return address -> {
            seen.add(address);
            return Optional.ofNullable(country);
        };
    }

    @Test
    void disabledIsUnknownAndNeverLooksUp() {
        GeoCountryService s = service(false, returning("US"));
        assertThat(s.check("49.36.1.1")).isEqualTo(GeoVerdict.UNKNOWN);
        assertThat(s.countryOf("49.36.1.1")).isEmpty();
        assertThat(seen).isEmpty();
    }

    @Test
    void privateLoopbackAndGarbageAreUnknownWithoutLookup() {
        GeoCountryService s = service(true, returning("US"));
        for (String ip : new String[]{"10.0.0.1", "192.168.1.1", "172.16.0.1", "127.0.0.1", "169.254.1.1", "0.0.0.0",
                "::1", "fe80::1", "fd00::1", "::", "garbage", "example.com", "", "   ", "1.2.3", "999.1.1.1", null}) {
            assertThat(s.check(ip)).as("ip=%s", ip).isEqualTo(GeoVerdict.UNKNOWN);
        }
        assertThat(seen).isEmpty();
    }

    @Test
    void allowedCountryIsAllowed() {
        assertThat(service(true, returning("IN")).check("49.36.1.1")).isEqualTo(GeoVerdict.ALLOWED);
    }

    @Test
    void allowedCountryComparisonIgnoresCase() {
        assertThat(service(true, returning("in")).check("49.36.1.1")).isEqualTo(GeoVerdict.ALLOWED);
    }

    @Test
    void otherCountryIsForeign() {
        assertThat(service(true, returning("US")).check("8.8.8.8")).isEqualTo(GeoVerdict.FOREIGN);
    }

    @Test
    void countryOfReturnsCode() {
        assertThat(service(true, returning("US")).countryOf("8.8.8.8")).contains("US");
    }

    @Test
    void ipv4MappedIpv6IsUnwrappedBeforeLookup() throws Exception {
        GeoCountryService s = service(true, returning("IN"));
        assertThat(s.check("::ffff:49.36.1.1")).isEqualTo(GeoVerdict.ALLOWED);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getAddress()).hasSize(4);
        assertThat(seen.get(0)).isEqualTo(InetAddress.getByAddress(new byte[]{49, 36, 1, 1}));
    }

    @Test
    void mappedPrivateAddressIsUnknown() {
        GeoCountryService s = service(true, returning("US"));
        assertThat(s.check("::ffff:10.0.0.1")).isEqualTo(GeoVerdict.UNKNOWN);
        assertThat(seen).isEmpty();
    }

    @Test
    void publicIpv6IsLookedUp() {
        assertThat(service(true, returning("US")).check("2001:4860:4860::8888")).isEqualTo(GeoVerdict.FOREIGN);
        assertThat(seen).hasSize(1);
    }

    @Test
    void lookupThrowingIsUnknown() {
        GeoCountryService s = service(true, address -> {
            throw new IllegalStateException("db corrupt");
        });
        assertThat(s.check("8.8.8.8")).isEqualTo(GeoVerdict.UNKNOWN);
    }

    @Test
    void emptyLookupResultIsUnknown() {
        assertThat(service(true, returning(null)).check("8.8.8.8")).isEqualTo(GeoVerdict.UNKNOWN);
    }

    @Test
    void unavailableLookupIsUnknown() {
        assertThat(new GeoCountryService(propsEnabled(), null).check("8.8.8.8")).isEqualTo(GeoVerdict.UNKNOWN);
    }

    @Test
    void missingDbFileNeverThrows() {
        SecurityProperties props = propsEnabled();
        props.getGeo().setDbPath("/nonexistent/GeoLite2-Country.mmdb");
        MaxMindCountryLookup lookup = new MaxMindCountryLookup(props);
        lookup.open();
        assertThat(new GeoCountryService(props, lookup).check("8.8.8.8")).isEqualTo(GeoVerdict.UNKNOWN);
    }

    @Test
    void blankDbPathNeverThrows() {
        SecurityProperties props = propsEnabled();
        props.getGeo().setDbPath("");
        MaxMindCountryLookup lookup = new MaxMindCountryLookup(props);
        lookup.open();
        assertThat(new GeoCountryService(props, lookup).check("8.8.8.8")).isEqualTo(GeoVerdict.UNKNOWN);
    }

    private static SecurityProperties propsEnabled() {
        SecurityProperties props = new SecurityProperties();
        props.getGeo().setEnabled(true);
        return props;
    }
}
