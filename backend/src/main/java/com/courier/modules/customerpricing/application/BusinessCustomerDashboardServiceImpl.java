package com.courier.modules.customerpricing.application;

import com.courier.modules.customerpricing.application.BusinessCustomerDashboard.CustomerRow;
import com.courier.modules.customerpricing.application.BusinessCustomerDashboard.Totals;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain aggregate SQL over {@code customers} and {@code shipments}/{@code shipment_charges}
 * (read-only, matched on the consignor's mobile). Every query is scoped to the caller's company
 * explicitly — native queries bypass the Hibernate company filter, the same reason the dashboard
 * module scopes explicitly rather than trusting the implicit filter.
 */
@Slf4j
@Service
public class BusinessCustomerDashboardServiceImpl implements BusinessCustomerDashboardService {

    /** Payment modes not collected at the counter; an un-invoiced booking on one of these is still owed. */
    private static final String PENDING_MODES = "'TBB', 'TOPAY', 'TO_PAY', 'COD'";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('" + Roles.COMPANY_ADMIN + "')")
    public BusinessCustomerDashboard overview() {
        UUID companyId = CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. The dashboard belongs to a company."));
        byte[] company = TimeOrderedUuid.toBytes(companyId);
        Timestamp monthStart = Timestamp.from(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC));

        record Cust(UUID id, String code, String name, String mobile, String status, String mode, String freq) { }
        List<Cust> customers = rows(
                "SELECT id, customer_code, COALESCE(NULLIF(TRIM(company_name), ''), TRIM(CONCAT_WS(' ', first_name, last_name))), "
                        + "mobile, status, default_payment_mode, billing_frequency FROM customers "
                        + "WHERE company_id = :c AND deleted = 0 AND customer_type = 'BUSINESS' ORDER BY 3",
                company, null).stream()
                .map(r -> new Cust(uuid(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]))).toList();

        // mobile -> [mobile, bookings, thisMonth, lastAt, businessAmount, pendingAmount, qty, weightKg]
        Map<String, Object[]> bookings = new HashMap<>();
        for (Object[] r : rows("SELECT s.sender_contact, COUNT(*), SUM(s.created_at >= :m), MAX(s.created_at), "
                + "COALESCE(SUM(sc.net_amount), 0), "
                + "COALESCE(SUM(CASE WHEN pm.code IN (" + PENDING_MODES + ") AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines l "
                + "WHERE l.shipment_id = s.id AND l.deleted = 0) THEN sc.net_amount ELSE 0 END), 0), "
                + "COALESCE(SUM(s.number_of_packages), 0), COALESCE(SUM(s.actual_weight), 0) "
                + "FROM shipments s LEFT JOIN shipment_charges sc ON sc.shipment_id = s.id AND sc.deleted = 0 "
                + "LEFT JOIN master_payment_modes pm ON pm.id = s.payment_mode_id "
                + "WHERE s.company_id = :c AND s.deleted = 0 AND s.status <> 'CANCELLED' AND s.sender_contact IS NOT NULL "
                + "GROUP BY s.sender_contact", company, monthStart)) {
            bookings.put(str(r[0]), r);
        }

        // Outstanding on live invoices, per customer: total - paid at the counter - receipts.
        Map<UUID, BigDecimal> invoiceBalance = new HashMap<>();
        for (Object[] r : rows("SELECT customer_id, SUM(GREATEST(total_amount - prepaid_amount - amount_paid, 0)) FROM customer_invoices "
                + "WHERE company_id = :c AND deleted = 0 AND status <> 'CANCELLED' GROUP BY customer_id", company, null)) {
            invoiceBalance.put(uuid(r[0]), money(r[1]));
        }

        Map<String, Long> byMode = new LinkedHashMap<>();
        Map<String, Long> byFreq = new LinkedHashMap<>();
        for (String m : List.of("CREDIT", "PAID", "TOPAY")) byMode.put(m, 0L);
        for (String f : List.of("WEEKLY", "FIFTEEN_DAYS", "MONTHLY")) byFreq.put(f, 0L);

        List<CustomerRow> out = new ArrayList<>();
        long active = 0, month = 0, total = 0, qtyAll = 0;
        BigDecimal weightAll = BigDecimal.ZERO;
        BigDecimal business = BigDecimal.ZERO, pending = BigDecimal.ZERO;
        for (Cust c : customers) {
            if ("ACTIVE".equals(c.status())) active++;
            byMode.merge(c.mode(), 1L, Long::sum);
            byFreq.merge(c.freq(), 1L, Long::sum);
            Object[] b = bookings.get(c.mobile());
            long bTotal = b == null ? 0 : num(b[1]);
            long bMonth = b == null ? 0 : num(b[2]);
            BigDecimal amount = b == null ? BigDecimal.ZERO : money(b[4]);
            // pending = invoiced-but-unpaid balance + bookings not invoiced yet that were not paid at the counter
            BigDecimal due = (b == null ? BigDecimal.ZERO : money(b[5])).add(invoiceBalance.getOrDefault(c.id(), BigDecimal.ZERO));
            long qty = b == null ? 0 : num(b[6]);
            BigDecimal weight = b == null ? BigDecimal.ZERO : weight(b[7]);
            month += bMonth; total += bTotal; qtyAll += qty; weightAll = weightAll.add(weight);
            business = business.add(amount); pending = pending.add(due);
            out.add(new CustomerRow(c.id(), c.code(), c.name(), c.mobile(), c.status(), c.mode(), c.freq(),
                    bMonth, bTotal, qty, weight, amount, due, b == null || b[3] == null ? null : ((Timestamp) b[3]).toInstant()));
        }

        Totals totals = new Totals(customers.size(), active, customers.size() - active, month, total,
                qtyAll, weightAll, business, pending);
        return new BusinessCustomerDashboard(totals, byMode, byFreq, out);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, byte[] company, Timestamp monthStart) {
        var q = em.createNativeQuery(sql).setParameter("c", company);
        if (monthStart != null) q.setParameter("m", monthStart);
        return (List<Object[]>) q.getResultList();
    }

    private static UUID uuid(Object bytes) {
        ByteBuffer b = ByteBuffer.wrap((byte[]) bytes);
        return new UUID(b.getLong(), b.getLong());
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    private static BigDecimal weight(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString()).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
