package com.courier.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    private MockHttpServletRequest request(String remote) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        return request;
    }

    @Test
    void behindProxyUsesRealIpNotForwardedFor() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Real-IP", "49.36.1.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 49.36.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("49.36.1.1");
    }

    @Test
    void directConnectionIgnoresForwardingHeaders() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Real-IP", "49.36.1.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void invalidRealIpFallsBackToSocketAddress() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Real-IP", "not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void hostnameRealIpIsRejectedWithoutLookup() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Real-IP", "example.com");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.2");
    }

    private String viaProxy(String xRealIp) {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Real-IP", xRealIp);
        return resolver.resolve(request);
    }

    @Test
    void ipv4MappedPrivatePeerIsTrusted() {
        MockHttpServletRequest request = request("::ffff:172.18.0.5");
        request.addHeader("X-Real-IP", "49.36.1.1");
        assertThat(resolver.resolve(request)).isEqualTo("49.36.1.1");
    }

    @Test
    void ipv6UniqueLocalPeerIsTrusted() {
        MockHttpServletRequest request = request("fd00::1");
        request.addHeader("X-Real-IP", "49.36.1.1");
        assertThat(resolver.resolve(request)).isEqualTo("49.36.1.1");
    }

    @Test
    void hostnameLikeAndMalformedRealIpsFallBackToSocketAddress() {
        for (String bad : new String[] {"dead.beef", "ab.cd", "bad.cafe", "1.2.3.4.5", "999.1.1.1",
                "1.2.3", "0x7f.1", "1.1.1.1, 2.2.2.2", "fe80::1%eth0", "1.2:1.2.3.4"}) {
            assertThat(viaProxy(bad)).as(bad).isEqualTo("172.18.0.5");
        }
    }

    @Test
    void overLongRealIpFallsBackToSocketAddress() {
        assertThat(viaProxy("1".repeat(46))).isEqualTo("172.18.0.5");
        assertThat(viaProxy("2001:db8:" + "0:".repeat(20) + "1")).isEqualTo("172.18.0.5");
    }

    @Test
    void whitespacePaddedRealIpIsTrimmedThenAccepted() {
        assertThat(viaProxy(" 49.36.1.1 ")).isEqualTo("49.36.1.1");
    }

    @Test
    void embeddedIpv4RealIpIsAccepted() {
        assertThat(viaProxy("::ffff:1.2.3.4")).isEqualTo("::ffff:1.2.3.4");
    }

    @Test
    void noHeadersReturnsSocketAddress() {
        assertThat(resolver.resolve(request("172.18.0.5"))).isEqualTo("172.18.0.5");
    }

    @Test
    void loopbackProxyIsTrusted() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "2001:db8::1");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }
}
