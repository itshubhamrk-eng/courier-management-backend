package com.courier.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the client IP without trusting client-supplied headers.
 *
 * <p>The backend sits behind nginx on a private network, which overwrites
 * {@code X-Real-IP} with the real socket peer. So {@code X-Real-IP} is honoured
 * only when the direct peer is itself a private/loopback address (i.e. the proxy);
 * a direct connection from a public address ignores every forwarding header,
 * since the client could have forged them. {@code X-Forwarded-For} is never used.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_LENGTH = 45;

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isTrustedProxy(remote)) {
            String real = request.getHeader("X-Real-IP");
            if (real != null) {
                String candidate = real.trim();
                if (parseLiteral(candidate) != null) {
                    return truncate(candidate);
                }
            }
        }
        return truncate(remote);
    }

    private static boolean isTrustedProxy(String remote) {
        InetAddress address = parseLiteral(remote);
        if (address == null) {
            return false;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        // IPv6 unique-local fc00::/7 is not reported as site-local by the JDK.
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /** Parses an IPv4/IPv6 literal only; anything else returns null and never triggers DNS. */
    private static InetAddress parseLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        try {
            if (value.indexOf(':') < 0) {
                byte[] v4 = parseIpv4(value);
                return v4 == null ? null : InetAddress.getByAddress(v4);
            }
            // Shape check first: hex digits, ':' and a trailing embedded IPv4 only.
            // A string containing ':' is always parsed as an IPv6 literal by the JDK
            // (never resolved as a hostname), and a malformed one just throws.
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c != ':' && c != '.' && Character.digit(c, 16) < 0) {
                    return null;
                }
            }
            String tail = value.substring(value.lastIndexOf(':') + 1);
            if (value.indexOf('.') >= 0 && parseIpv4(tail) == null) {
                return null;
            }
            if (value.indexOf('.') >= 0 && value.indexOf('.') < value.lastIndexOf(':')) {
                return null;
            }
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** Exactly four dot-separated decimal octets 0-255, digits only; else null. */
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

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
