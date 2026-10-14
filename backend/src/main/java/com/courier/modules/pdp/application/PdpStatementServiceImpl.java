package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpEarningRepository;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import com.courier.modules.pdp.domain.PdpStatement;
import com.courier.modules.pdp.domain.PdpStatementRepository;
import com.courier.modules.pdp.domain.PdpStatementStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdpStatementServiceImpl implements PdpStatementService {

    private static final String ENTITY = "PdpStatement";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final PdpStatementRepository statements;
    private final PdpEarningRepository earnings;
    private final BranchService branchService;
    private final AuditService auditService;
    private final UserService userService;

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public StatementView generate(UUID branchId, LocalDate from, LocalDate to, String remarks) {
        return doGenerate(branchId, from, to, remarks);
    }

    @Override
    @Transactional
    public StatementView generateScheduled(UUID branchId, LocalDate from, LocalDate to, String remarks) {
        return doGenerate(branchId, from, to, remarks);
    }

    private StatementView doGenerate(UUID branchId, LocalDate from, LocalDate to, String remarks) {
        UUID companyId = requireCompany();
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessRuleException("A valid period (from, to) is required.");
        }
        Branch partner = branchService.getById(branchId);
        if (partner.getBranchType() != BranchType.PICKUP_DELIVERY_PARTNER) {
            throw new BusinessRuleException("Branch %s is not a Pickup-Delivery Partner.".formatted(partner.getBranchCode()));
        }
        if (statements.countOverlapping(companyId, branchId, from, to) > 0) {
            throw new BusinessRuleException("This partner already has a statement overlapping %s to %s.".formatted(from, to));
        }
        List<PdpEarning> billable = earnings.findBillable(companyId, branchId, from, to);
        if (billable.isEmpty()) {
            throw new BusinessRuleException("This partner has no unbilled earnings between %s and %s.".formatted(from, to));
        }
        PdpStatement s = PdpStatement.builder().statementNo(nextNo(companyId)).branchId(branchId)
                .periodFrom(from).periodTo(to).remarks(remarks == null || remarks.isBlank() ? null : remarks.trim())
                .build();
        recompute(s, billable);
        PdpStatement saved = statements.save(s);
        billable.forEach(e -> {
            e.setStatementId(saved.getId());
            e.setStatus(PdpEarningStatus.BILLED);
            earnings.save(e);
        });
        auditService.record(AuditAction.PDP_STATEMENT_GENERATED, ENTITY, saved.getId(),
                Map.of("statementNo", saved.getStatementNo(), "net", saved.getNetPayable().toPlainString()));
        return view(companyId, saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<PdpStatement> search(UUID branchId, PdpStatementStatus status, Pageable pageable) {
        UUID own = ownPartnerScope();
        Page<PdpStatement> page = statements.search(requireCompany(), own != null ? own : branchId, status, pageable);
        // A partner only sees statements the company has approved, never a draft in progress.
        return own == null ? page : new org.springframework.data.domain.PageImpl<>(
                page.getContent().stream().filter(s -> s.getStatus() != PdpStatementStatus.DRAFT).toList(),
                pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public StatementView get(UUID id) {
        UUID companyId = requireCompany();
        PdpStatement s = load(id, companyId);
        UUID own = ownPartnerScope();
        if (own != null && (!own.equals(s.getBranchId()) || s.getStatus() == PdpStatementStatus.DRAFT)) {
            throw new ResourceNotFoundException(ENTITY, id);
        }
        return view(companyId, s);
    }

    /** The caller's own branch when branch-bound (a partner), else null for a company admin. */
    private UUID ownPartnerScope() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (caller.isPlatformTier() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return null;
        }
        UUID branchId = userService.getById(caller.userId()).getBranchId();
        return branchId != null ? branchId : new UUID(0L, 0L);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public StatementView approve(UUID id) {
        UUID companyId = requireCompany();
        PdpStatement s = load(id, companyId);
        s.approve();
        PdpStatement saved = statements.save(s);
        auditService.record(AuditAction.PDP_STATEMENT_APPROVED, ENTITY, saved.getId(), Map.of("statementNo", saved.getStatementNo()));
        return view(companyId, saved);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public StatementView markPaid(UUID id, LocalDate paidOn, String reference) {
        UUID companyId = requireCompany();
        PdpStatement s = load(id, companyId);
        s.markPaid(paidOn, reference);
        PdpStatement saved = statements.save(s);
        auditService.record(AuditAction.PDP_STATEMENT_PAID, ENTITY, saved.getId(),
                Map.of("statementNo", saved.getStatementNo(), "paidOn", paidOn.toString()));
        return view(companyId, saved);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        PdpStatement s = load(id, companyId);
        if (s.getStatus() != PdpStatementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft statement can be deleted.");
        }
        earnings.findByStatement(companyId, s.getId()).forEach(e -> {
            e.setStatementId(null);
            e.setStatus(PdpEarningStatus.ACCRUED);
            earnings.save(e);
        });
        s.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        statements.save(s);
        auditService.record(AuditAction.PDP_STATEMENT_DELETED, ENTITY, s.getId(), Map.of("statementNo", s.getStatementNo()));
    }

    // ------------------------------------------------------------------ helpers

    private static void recompute(PdpStatement s, List<PdpEarning> lines) {
        BigDecimal pickup = BigDecimal.ZERO;
        BigDecimal delivery = BigDecimal.ZERO;
        int pc = 0;
        int dc = 0;
        for (PdpEarning e : lines) {
            if (e.getEarningType() == PdpEarningType.PICKUP) {
                pickup = pickup.add(e.getTotalAmount());
                pc++;
            } else {
                delivery = delivery.add(e.getTotalAmount());
                dc++;
            }
        }
        s.setPickupCount(pc);
        s.setDeliveryCount(dc);
        s.setPickupTotal(pickup);
        s.setDeliveryTotal(delivery);
        s.setNetPayable(pickup.add(delivery));
    }

    private StatementView view(UUID companyId, PdpStatement s) {
        statements.flush();
        return new StatementView(s, earnings.findByStatement(companyId, s.getId()),
                earnings.countUnrated(companyId, s.getBranchId(), s.getPeriodFrom(), s.getPeriodTo()));
    }

    private PdpStatement load(UUID id, UUID companyId) {
        return statements.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /** PS-yyyyMMdd-NNNN per company per day; the unique key backstops a race. */
    private String nextNo(UUID companyId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        long n = statements.countCreatedBetween(companyId, from, from.plusSeconds(86_400)) + 1;
        return "PS-%tY%<tm%<td-%04d".formatted(today, n);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Statements belong to a company."));
    }
}
