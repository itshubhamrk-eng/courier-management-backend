package com.courier.modules.security.domain;

/** Result of an IP geolocation check against the allowed country. */
public enum GeoVerdict {
    /** Resolved to the allowed country. */
    ALLOWED,
    /** Resolved to any other country. */
    FOREIGN,
    /** Geo disabled, non-public/garbage/null IP, or the lookup failed. Never treat as foreign. */
    UNKNOWN
}
