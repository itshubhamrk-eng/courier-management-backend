package com.courier.modules.shipment.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Package-level identifier printed on each shipment label: {@code <AWB>-NNN}, e.g.
 * {@code AWB10001-002} is package 2 of shipment {@code AWB10001}. There is still only one
 * shipment per AWB — this just lets a scan of any label resolve it, and say which package.
 */
public record PackageId(String awb, int packageNo) {

    private static final Pattern FORMAT = Pattern.compile("^(.+)-(\\d{3})$");

    /** Splits a scanned value into AWB + package number; empty when it isn't package-shaped.
     *  Does not check the AWB exists or the number is in range — callers resolve the AWB
     *  exact-match first, so a real tracking number that happens to end in {@code -NNN}
     *  still wins. */
    public static Optional<PackageId> parse(String scanned) {
        if (scanned == null) return Optional.empty();
        Matcher m = FORMAT.matcher(scanned.trim());
        if (!m.matches()) return Optional.empty();
        int n = Integer.parseInt(m.group(2));
        return n < 1 ? Optional.empty() : Optional.of(new PackageId(m.group(1), n));
    }
}
