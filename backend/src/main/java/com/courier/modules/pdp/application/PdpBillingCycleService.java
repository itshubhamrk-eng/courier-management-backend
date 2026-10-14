package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.pdp.domain.PdpBillingSetting;
import com.courier.modules.pdp.domain.PdpBillingSettingRepository;
import com.courier.modules.pdp.domain.PdpEarningRepository;
import com.courier.modules.pdp.domain.PdpStatementRepository;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-partner billing frequency and the scheduled statements built from it. A partner with no
 * setting is billed monthly. A draft statement is created when a partner's cycle closes and has
 * unbilled earnings; an existing statement reaching the cycle end makes the run a no-op, so the
 * job is idempotent however often it fires. Statements are only drafts — approving stays manual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdpBillingCycleService {

    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final PdpBillingSettingRepository settings;
    private final PdpStatementRepository statements;
    private final PdpEarningRepository earnings;
    private final PdpStatementService statementService;
    private final BranchService branchService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager em;

    /** One partner's billing position as of a day. */
    public record ScheduleRow(UUID branchId, String branchCode, String branchName, BillingFrequency frequency,
                              LocalDate lastBilledTo, long unbilledEarnings, LocalDate dueFrom, LocalDate dueTo) {
        public boolean isDue() {
            return dueTo != null;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public List<ScheduleRow> schedule(LocalDate today) {
        UUID company = requireCompany();
        return partners(company).stream().map(p -> row(company, p, today)).toList();
    }

    @Transactional
    @PreAuthorize(ADMIN)
    public BillingFrequency setFrequency(UUID branchId, BillingFrequency frequency) {
        UUID company = requireCompany();
        Branch partner = branchService.getById(branchId);
        if (partner.getBranchType() != BranchType.PICKUP_DELIVERY_PARTNER) {
            throw new BusinessRuleException("Branch %s is not a Pickup-Delivery Partner.".formatted(partner.getBranchCode()));
        }
        if (frequency == null) {
            throw new BusinessRuleException("A billing frequency is required.");
        }
        PdpBillingSetting s = settings.findByBranch(company, branchId)
                .orElseGet(() -> PdpBillingSetting.builder().branchId(branchId).build());
        s.setBillingFrequency(frequency);
        settings.save(s);
        auditService.record(AuditAction.PDP_BILLING_FREQUENCY_CHANGED, "PdpBillingSetting", branchId,
                Map.of("branch", partner.getBranchCode(), "frequency", frequency.name()));
        return frequency;
    }

    /** The "Generate due statements now" button: the bound company, as of {@code today}. */
    @PreAuthorize(ADMIN)
    public int generateDue(LocalDate today) {
        return runForBoundCompany(today);
    }

    /** Every company with an active partner; one failing company never stops the others. */
    public int runAll(LocalDate today) {
        int created = 0;
        for (UUID company : companiesToBill()) {
            try {
                created += CompanyContext.runAs(company, () -> runForBoundCompany(today));
            } catch (Exception e) {
                log.error("Partner billing cycle failed for company {}", company, e);
            }
        }
        log.info("Partner billing cycle for {}: {} statement(s) created", today, created);
        return created;
    }

    int runForBoundCompany(LocalDate today) {
        UUID company = requireCompany();
        int created = 0;
        for (Partner p : partners(company)) {
            ScheduleRow row = row(company, p, today);
            if (!row.isDue()) {
                continue;
            }
            try {
                statementService.generateScheduled(p.id(), row.dueFrom(), row.dueTo(),
                        "Auto-generated for the " + row.frequency().name().toLowerCase().replace('_', ' ') + " cycle");
                created++;
            } catch (Exception e) {
                log.error("Could not bill partner {}", p.code(), e);
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ helpers

    private record Partner(UUID id, String code, String name) {
    }

    private ScheduleRow row(UUID company, Partner p, LocalDate today) {
        BillingFrequency freq = settings.findByBranch(company, p.id()).map(PdpBillingSetting::getBillingFrequency)
                .orElse(PdpBillingSetting.DEFAULT);
        LocalDate last = statements.latestPeriodEnd(company, p.id());
        LocalDate cycleEnd = com.courier.modules.customerbilling.application.BillingCycle.lastClosedCycleEnd(freq, today);
        LocalDate earliest = earnings.earliestUnbilled(company, p.id(), cycleEnd);
        var window = PdpBillingCycle.due(freq, today, last, earliest);
        return new ScheduleRow(p.id(), p.code(), p.name(), freq, last, earnings.countUnbilled(company, p.id()),
                window.map(PdpBillingCycle.Window::from).orElse(null), window.map(PdpBillingCycle.Window::to).orElse(null));
    }

    @SuppressWarnings("unchecked")
    private List<Partner> partners(UUID company) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, branch_code, branch_name FROM branches "
                        + "WHERE company_id = :c AND branch_type = 'PICKUP_DELIVERY_PARTNER' AND status = 'ACTIVE' "
                        + "AND deleted = 0 ORDER BY branch_name")
                .setParameter("c", TimeOrderedUuid.toBytes(company)).getResultList();
        return rows.stream().map(r -> new Partner(uuid((byte[]) r[0]), String.valueOf(r[1]), String.valueOf(r[2]))).toList();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> companiesToBill() {
        List<byte[]> rows = em.createNativeQuery("SELECT DISTINCT company_id FROM branches "
                + "WHERE branch_type = 'PICKUP_DELIVERY_PARTNER' AND status = 'ACTIVE' AND deleted = 0").getResultList();
        return rows.stream().map(PdpBillingCycleService::uuid).toList();
    }

    private static UUID uuid(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        return new UUID(bb.getLong(), bb.getLong());
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Billing belongs to a company."));
    }

    /** Today in UTC, the clock the cycle job and the screens share. */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
