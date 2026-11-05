package com.courier.modules.security.application.scan.rules;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Collapses a client IP to its network: IPv4 to the /24, IPv6 to the /64. Addresses that say
 * nothing about where a client is (loopback, private, link-local, carrier-grade NAT, unique-
 * local, unparseable) yield empty so they never count as a "different" location.
 * Only literal IPs are ever parsed; no DNS lookup can happen.
 */
final class IpPrefix {

    private static final Pattern V4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern V6 = Pattern.compile("[0-9a-fA-F:.]+");

    private IpPrefix() {
    }

    static Optional<String> of(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        String s = ip.trim();
        boolean v4 = V4.matcher(s).matches();
        if (!v4 && !(s.contains(":") && V6.matcher(s).matches())) {
            return Optional.empty();
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(s);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return Optional.empty();
        }
        byte[] b = addr.getAddress();
        if (addr instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) {
                return Optional.empty(); // carrier-grade NAT 100.64.0.0/10
            }
            return Optional.of((b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff));
        }
        if (addr instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) {
                return Optional.empty(); // unique-local fc00::/7
            }
            return Optional.of(HexFormat.of().formatHex(b, 0, 8));
        }
        return Optional.empty();
    }
}
