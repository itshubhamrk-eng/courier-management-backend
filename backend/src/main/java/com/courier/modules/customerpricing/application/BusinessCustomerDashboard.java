package com.courier.modules.customerpricing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Company-wide overview of business customers: counts, payment/billing mix and per-customer
 * booking activity. Bookings are matched to a customer by the consignor's mobile number — a
 * shipment stores plain sender values, never a customer id — so the booking figures are
 * "bookings whose consignor contact is this customer's mobile", cancelled ones excluded.
 *
 * <p>Quantity is the sum of {@code number_of_packages}; weight is the sum of {@code actual_weight} in kg
 * (not the chargeable weight). Amounts are the shipments' {@code net_amount}. <b>Pending</b> is the
 * outstanding balance of the customer's live (non-cancelled) invoices plus bookings not yet on an invoice
 * that were booked on To Be Billed, To Pay or COD; a booking paid at the counter is never pending.
 */
public record BusinessCustomerDashboard(
        Totals totals,
        Map<String, Long> byPaymentMode,
        Map<String, Long> byBillingFrequency,
        List<CustomerRow> customers) {

    public record Totals(long customers, long activeCustomers, long inactiveCustomers,
                         long bookingsThisMonth, long bookingsTotal,
                         long totalQuantity, BigDecimal totalWeight,
                         BigDecimal totalBusiness, BigDecimal paymentPending) {
    }

    public record CustomerRow(UUID customerId, String customerCode, String displayName, String mobile,
                              String status, String paymentMode, String billingFrequency,
                              long bookingsThisMonth, long bookingsTotal,
                              long quantity, BigDecimal weight,
                              BigDecimal businessAmount, BigDecimal pendingAmount,
                              Instant lastBookingAt) {
    }
}
