package com.courier.modules.communication.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Realistic sample values for every {@code {{variable}}} a template may use — shipment events
 * and the daily business summary both. Meta refuses a template without an example per
 * placeholder, and the template preview of the daily summary has no shipment to render from.
 */
public final class TemplateSampleValues {

    private static final Map<String, String> ALL = build();

    private TemplateSampleValues() {
    }

    public static Map<String, String> all() {
        return ALL;
    }

    /** The built-in samples with a template's own overrides applied on top. */
    public static Map<String, String> merged(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return ALL;
        }
        Map<String, String> m = new LinkedHashMap<>(ALL);
        overrides.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                m.put(k, v);
            }
        });
        return java.util.Collections.unmodifiableMap(m);
    }

    private static Map<String, String> build() {
        Map<String, String> m = new LinkedHashMap<>();
        // shipment events
        m.put("customerName", "Rahul Sharma");
        m.put("companyName", "Amazing Logistics");
        m.put("shipmentNumber", "SHP-000123");
        m.put("trackingNumber", "AWB123456789");
        m.put("pickupLocation", "Pune Hub");
        m.put("deliveryLocation", "Mumbai Hub");
        m.put("amount", "450.00");
        m.put("deliveryDate", "16 Oct 2026");
        m.put("receiverName", "Priya Verma");
        m.put("trackingUrl", "https://example.com/track/AWB123456789");
        m.put("podUrl", "https://example.com/pod/sample.jpg");
        // daily business summary
        m.put("reportDate", "13 Oct 2026");
        m.put("bookings", "15");
        m.put("revenue", "8,240.00");
        m.put("rechargeAmount", "50,000.00");
        m.put("rechargeCount", "3");
        m.put("delivered", "20");
        m.put("cancelled", "2");
        m.put("codToPaySettled", "2,120.00");
        m.put("inTransit", "8");
        m.put("outForDelivery", "5");
        return java.util.Collections.unmodifiableMap(m);
    }
}
