package com.courier.modules.security.application.scan.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpPrefixTest {

    @Test
    void ipv4IsGroupedBySlash24() {
        assertThat(IpPrefix.of("8.8.8.8")).contains("8.8.8");
        assertThat(IpPrefix.of("8.8.8.200")).contains("8.8.8");
        assertThat(IpPrefix.of("8.8.9.1")).contains("8.8.9");
    }

    @Test
    void ipv6IsGroupedBySlash64() {
        assertThat(IpPrefix.of("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isEqualTo(IpPrefix.of("2001:db8:1:2::1"))
                .isPresent();
        assertThat(IpPrefix.of("2001:db8:1:2::1")).isNotEqualTo(IpPrefix.of("2001:db8:1:3::1"));
    }

    @Test
    void loopbackPrivateLinkLocalAndUlaAreIgnored() {
        assertThat(IpPrefix.of("127.0.0.1")).isEmpty();
        assertThat(IpPrefix.of("10.1.2.3")).isEmpty();
        assertThat(IpPrefix.of("172.16.5.5")).isEmpty();
        assertThat(IpPrefix.of("192.168.1.9")).isEmpty();
        assertThat(IpPrefix.of("169.254.1.1")).isEmpty();
        assertThat(IpPrefix.of("::1")).isEmpty();
        assertThat(IpPrefix.of("fe80::1")).isEmpty();
        assertThat(IpPrefix.of("fd00::1")).isEmpty();
    }

    @Test
    void garbageAndBlankAreIgnored() {
        assertThat(IpPrefix.of(null)).isEmpty();
        assertThat(IpPrefix.of("")).isEmpty();
        assertThat(IpPrefix.of("example.com")).isEmpty();
        assertThat(IpPrefix.of("999.1.1.1")).isEmpty();
        assertThat(IpPrefix.of("1.1.1.256")).isEmpty();
    }
}
