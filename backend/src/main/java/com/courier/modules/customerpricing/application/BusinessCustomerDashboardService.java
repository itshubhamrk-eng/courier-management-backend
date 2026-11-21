package com.courier.modules.customerpricing.application;

/** Read-only overview for the TBB Customer dashboard. {@code COMPANY_ADMIN} only, like the menu that hosts it. */
public interface BusinessCustomerDashboardService {

    BusinessCustomerDashboard overview();

    /** Monthly business (net amount) and booking count, last {@code months} months; one customer or all TBB customers. */
    java.util.List<RevenuePoint> revenueTrend(int months, java.util.UUID customerId);

    record RevenuePoint(String month, java.math.BigDecimal amount, long bookings) { }
}
