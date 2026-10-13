package com.courier.modules.customerpricing.application;

/** Read-only overview for the Business Customer dashboard. {@code COMPANY_ADMIN} only, like the menu that hosts it. */
public interface BusinessCustomerDashboardService {

    BusinessCustomerDashboard overview();
}
