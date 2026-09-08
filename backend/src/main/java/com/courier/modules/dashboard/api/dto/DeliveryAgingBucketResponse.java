package com.courier.modules.dashboard.api.dto;

/**
 * One row of the branch dashboard's Delivery Pending aging breakdown — how many of the
 * branch's own pending-delivery shipments (IN_SCAN/OUT_FOR_DELIVERY) have sat that long
 * since being received (last IN_SCAN scan-in), not since booking. See
 * {@code DashboardServiceImpl.deliveryPendingAging}.
 */
public record DeliveryAgingBucketResponse(String label, long count) {
}
