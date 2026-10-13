package com.courier.modules.customerbilling.application;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.customerbilling.domain.CustomerInvoiceRepository;
import com.courier.modules.customerbilling.domain.InvoiceSource;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bills every ACTIVE business customer whose cycle has closed. For a customer the cycle end is
 * {@link BillingCycle#lastClosedCycleEnd}; an AUTO invoice already issued for that end date means
 * "done", so the job is idempotent however often it runs. Anything unbilled up to the cycle end —
 * including bookings a missed run left behind — goes on the invoice.
 *
 * <p>Runs unauthenticated from the job, so each company is wrapped in {@code CompanyContext.runAs}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final CustomerInvoiceGenerator generator;
    private final CustomerInvoiceRepository invoices;

    @PersistenceContext
    private EntityManager em;

    /** Every company that has at least one active business customer. */
    @SuppressWarnings("unchecked")
    public List<UUID> companiesToBill() {
        List<byte[]> rows = em.createNativeQuery("SELECT DISTINCT company_id FROM customers "
                + "WHERE customer_type = 'BUSINESS' AND status = 'ACTIVE' AND deleted = 0").getResultList();
        return rows.stream().map(b -> {
            ByteBuffer bb = ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }).toList();
    }

    /** All companies; one failing company never stops the others. Returns invoices issued. */
    public int runAll(LocalDate today) {
        int issued = 0;
        for (UUID companyId : companiesToBill()) {
            try {
                issued += CompanyContext.runAs(companyId, () -> runForBoundCompany(today));
            } catch (Exception e) {
                log.error("Billing cycle failed for company {}", companyId, e);
            }
        }
        log.info("Billing cycle for {}: {} invoice(s) issued", today, issued);
        return issued;
    }

    /** The company bound in {@link CompanyContext}. */
    @SuppressWarnings("unchecked")
    public int runForBoundCompany(LocalDate today) {
        UUID company = CompanyContext.getCompanyId().orElseThrow();
        List<Object[]> customers = em.createNativeQuery("SELECT id, billing_frequency FROM customers "
                        + "WHERE company_id = :c AND customer_type = 'BUSINESS' AND status = 'ACTIVE' AND deleted = 0")
                .setParameter("c", TimeOrderedUuid.toBytes(company)).getResultList();
        int issued = 0;
        for (Object[] row : customers) {
            ByteBuffer bb = ByteBuffer.wrap((byte[]) row[0]);
            UUID customerId = new UUID(bb.getLong(), bb.getLong());
            LocalDate cycleEnd = BillingCycle.lastClosedCycleEnd(BillingFrequency.valueOf(String.valueOf(row[1])), today);
            if (invoices.autoInvoiceExists(company, customerId, cycleEnd)) {
                continue;
            }
            if (generator.generate(customerId, cycleEnd, InvoiceSource.AUTO).isPresent()) {
                issued++;
            }
        }
        return issued;
    }
}
