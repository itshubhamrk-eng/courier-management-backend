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
        boolean hasSeparator = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.' || c == ':') {
                hasSeparator = true;
            } else if (Character.digit(c, 16) < 0) {
                return null;
            }
        }
        if (!hasSeparator) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
