package com.courier.shared.useragent;

/**
 * Best-effort, dependency-free classification of a {@code User-Agent} header for
 * display purposes only — login history, active sessions and the activity log.
 *
 * <p>Never a security signal: the header is entirely client-controlled. Deliberately
 * not shared with {@code SessionService.DeviceInfo.inferType}, which predates this and
 * is already covered by its own tests — duplicating ~15 lines here is cheaper than a
 * cross-module refactor of tested code for a purely cosmetic string.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public record Parsed(String device, String browser, String os) {
    }

    public static Parsed parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new Parsed("UNKNOWN", "Unknown", "Unknown");
        }
        String ua = userAgent.toLowerCase();
        return new Parsed(device(ua), browser(ua), os(ua));
    }

    private static String device(String ua) {
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "MOBILE";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "TABLET";
        }
        if (ua.contains("okhttp") || ua.contains("curl") || ua.contains("postman")
                || ua.contains("java") || ua.contains("python")) {
            return "API_CLIENT";
        }
        return "DESKTOP";
    }

    /** Order matters: Edge and Opera both carry "Chrome" in their own UA string. */
    private static String browser(String ua) {
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/") || ua.contains("fxios/")) {
            return "Firefox";
        }
        if (ua.contains("safari/") && !ua.contains("chrome")) {
            return "Safari";
        }
        if (ua.contains("okhttp") || ua.contains("curl") || ua.contains("postman")) {
            return "API Client";
        }
        return "Unknown";
    }

    private static String os(String ua) {
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }
}
