package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.pdp.domain.PdpRateCard;
import com.courier.modules.pdp.domain.PdpRateCardRepository;
import com.courier.modules.pdp.domain.PdpRateSlab;
import com.courier.modules.pdp.domain.PdpRateSlabRepository;
import com.courier.modules.pdp.domain.PdpRateType;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pickup / delivery rate cards of Pickup-Delivery Partners. A card belongs to one partner
 * (a Branch of type {@code PICKUP_DELIVERY_PARTNER}), one {@link PdpRateType} and one district
 * (or all). Weight charge = max(actual, card minimum) x the covering slab's rate per kg.
 * Slabs are half-open {@code [min, max)}, must not overlap, and only the last may be open-ended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdpRateServiceImpl implements PdpRateService {

    private static final String ENTITY = "PdpRate";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final PdpRateCardRepository rates;
    private final PdpRateSlabRepository slabs;
    private final BranchService branchService;
    private final AuditService auditService;
    private final UserService userService;

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PdpRateView create(SaveCommand c) {
        UUID companyId = requireCompany();
        Branch partner = requirePartner(c.branchId());
        PdpRateCard card = PdpRateCard.builder().branchId(partner.getId()).rateType(requireType(c)).build();
        return persist(companyId, partner, card, c);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PdpRateView update(UUID id, SaveCommand c) {
        UUID companyId = requireCompany();
        PdpRateCard card = load(id, companyId);
        if (c.expectedVersion() != null && !Objects.equals(card.getVersion(), c.expectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(PdpRateCard.class, card.getId());
        }
        if (c.rateType() != null && c.rateType() != card.getRateType()) {
            throw new BusinessRuleException("A rate card's type cannot be changed; create a new card instead.");
        }
        if (c.branchId() != null && !c.branchId().equals(card.getBranchId())) {
            throw new BusinessRuleException("A rate card's partner cannot be changed; create a new card instead.");
        }
        Branch partner = requirePartner(card.getBranchId());
        return persist(companyId, partner, card, c);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public PdpRateView get(UUID id) {
        UUID companyId = requireCompany();
        PdpRateCard card = load(id, companyId);
        UUID own = ownPartnerScope();
        if (own != null && !own.equals(card.getBranchId())) {
            throw new ResourceNotFoundException(ENTITY, id);
        }
        return view(companyId, card);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<PdpRateView> search(UUID branchId, PdpRateType rateType) {
        UUID companyId = requireCompany();
        UUID own = ownPartnerScope();
        return rates.search(companyId, own != null ? own : branchId, rateType).stream()
                .map(r -> view(companyId, r)).toList();
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PdpRateView activate(UUID id) {
        UUID companyId = requireCompany();
        PdpRateCard card = load(id, companyId);
        requireNoActiveOverlap(companyId, card, card.getEffectiveFrom(), card.getEffectiveTo());
        card.activate();
        PdpRateCard saved = rates.save(card);
        auditService.record(AuditAction.PDP_RATE_ACTIVATED, ENTITY, saved.getId(),
                Map.of("branchId", saved.getBranchId().toString()));
        return view(companyId, saved);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PdpRateView deactivate(UUID id) {
        UUID companyId = requireCompany();
        PdpRateCard card = load(id, companyId);
        card.deactivate();
        PdpRateCard saved = rates.save(card);
        auditService.record(AuditAction.PDP_RATE_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("branchId", saved.getBranchId().toString()));
        return view(companyId, saved);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        PdpRateCard card = load(id, companyId);
        UUID actor = SecurityUtils.getCurrentUserId().orElse(null);
        slabs.findByRate(companyId, card.getId()).forEach(s -> {
            s.softDelete(actor);
            slabs.save(s);
        });
        card.softDelete(actor);
        rates.save(card);
        auditService.record(AuditAction.PDP_RATE_DELETED, ENTITY, card.getId(),
                Map.of("branchId", card.getBranchId().toString()));
        log.warn("PDP rate {} deleted in company {} by {}", id, companyId, actor());
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = BusinessRuleException.class)
    @PreAuthorize(READ)
    public Quote calculate(QuoteCommand q) {
        if (q.branchId() != null) {
            UUID own = ownPartnerScope();
            if (own != null && !own.equals(q.branchId())) {
                throw new BusinessRuleException("You can only price your own rates.");
            }
        }
        return quote(q);
    }

    // noRollbackFor: the earning flows catch "no rate card" and record an UNRATED earning. Without it
    // the exception crossing this proxy would mark the CALLER's transaction rollback-only, and the
    // pickup link / delivery would fail at commit even though the exception was handled.
    @Override
    @Transactional(readOnly = true, noRollbackFor = BusinessRuleException.class)
    public Quote quote(QuoteCommand q) {
        UUID companyId = requireCompany();
        if (q.branchId() == null || q.rateType() == null) {
            throw new BusinessRuleException("A partner and a rate type are required.");
        }
        if (q.actualWeight() == null || q.actualWeight().signum() <= 0) {
            throw new BusinessRuleException("Actual weight must be greater than zero.");
        }
        LocalDate date = q.date() == null ? LocalDate.now() : q.date();
        List<PdpRateCard> live = rates.findByBranchAndType(companyId, q.branchId(), q.rateType()).stream()
                .filter(PdpRateCard::isActive)
                .filter(r -> r.coversDate(date))
                .toList();
        PdpRateCard card = live.stream().filter(r -> q.districtId() != null && q.districtId().equals(r.getDistrictId()))
                .findFirst()
                .or(() -> live.stream().filter(r -> r.getDistrictId() == null).findFirst())
                .orElseThrow(() -> new BusinessRuleException(
                        "This partner has no active %s rate for that district on %s."
                                .formatted(q.rateType().name().toLowerCase(), date)));

        BigDecimal chargeable = q.actualWeight().max(card.getMinChargeableWeight());
        PdpRateSlab slab = slabs.findByRate(companyId, card.getId()).stream()
                .filter(s -> s.covers(chargeable))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("No weight slab of the rate card covers %s kg."
                        .formatted(chargeable.stripTrailingZeros().toPlainString())));
        BigDecimal weightCharge = money(chargeable.multiply(slab.getRatePerKg()));
        BigDecimal mathadi = q.rateType() == PdpRateType.DELIVERY ? card.getMathadiCharge() : BigDecimal.ZERO;
        BigDecimal oda = q.rateType() == PdpRateType.DELIVERY && q.odaApplicable()
                ? card.getOdaCharge() : BigDecimal.ZERO;
        return new Quote(card.getId(), q.rateType(), q.actualWeight(), chargeable, slab.getRatePerKg(),
                weightCharge, mathadi, oda, money(weightCharge.add(mathadi).add(oda)));
    }

    // ------------------------------------------------------------------ helpers

    /** The caller's own branch when branch-bound (a partner reads only its own rate cards), else null for a company admin. */
    private UUID ownPartnerScope() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (caller.isPlatformTier() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return null;
        }
        UUID branchId = userService.getCurrent().getBranchId();
        return branchId != null ? branchId : new UUID(0L, 0L);
    }

    private PdpRateView persist(UUID companyId, Branch partner, PdpRateCard card, SaveCommand c) {
        List<SlabInput> inputs = c.slabs() == null ? List.of() : c.slabs();
        if (inputs.isEmpty()) {
            throw new BusinessRuleException("A rate card needs at least one per-kg weight slab.");
        }
        card.setDistrictId(c.districtId());
        card.setMinChargeableWeight(orZero(c.minChargeableWeight()));
        card.setMathadiCharge(orZero(c.mathadiCharge()));
        card.setOdaCharge(orZero(c.odaCharge()));
        card.setFailedAttemptFee(orZero(c.failedAttemptFee()));
        card.setReturnFee(orZero(c.returnFee()));
        card.setEffectiveFrom(c.effectiveFrom());
        card.setEffectiveTo(c.effectiveTo());
        card.applyInvariants();
        if (card.isActive()) {
            requireNoActiveOverlap(companyId, card, c.effectiveFrom(), c.effectiveTo());
        }

        List<PdpRateSlab> newSlabs = inputs.stream()
                .map(i -> PdpRateSlab.builder().minWeight(i.minWeight()).maxWeight(i.maxWeight())
                        .ratePerKg(i.ratePerKg()).build())
                .sorted(Comparator.comparing(s -> s.getMinWeight() == null ? BigDecimal.ZERO : s.getMinWeight()))
                .toList();
        newSlabs.forEach(PdpRateSlab::applyInvariants);
        requireValidSlabs(newSlabs);

        PdpRateCard saved = rates.save(card);
        UUID actor = SecurityUtils.getCurrentUserId().orElse(null);
        slabs.findByRate(companyId, saved.getId()).forEach(s -> {
            s.softDelete(actor);
            slabs.save(s);
        });
        for (PdpRateSlab slab : newSlabs) {
            slab.setRateId(saved.getId());
            slabs.save(slab);
        }
        log.info("PDP {} rate {} saved for partner {} in company {} by {} ({} slab(s))",
                saved.getRateType(), saved.getId(), partner.getBranchCode(), companyId, actor(), newSlabs.size());
        auditService.record(AuditAction.PDP_RATE_SAVED, ENTITY, saved.getId(),
                Map.of("branchId", partner.getId().toString(), "rateType", saved.getRateType().name(),
                        "slabs", newSlabs.size()));
        return new PdpRateView(saved, partner.getBranchCode(), partner.getBranchName(),
                slabs.findByRate(companyId, saved.getId()));
    }

    /** At most one ACTIVE card per (partner, type, district) on any given day. */
    private void requireNoActiveOverlap(UUID companyId, PdpRateCard card, LocalDate from, LocalDate to) {
        boolean clash = rates.findByBranchAndType(companyId, card.getBranchId(), card.getRateType()).stream()
                .filter(PdpRateCard::isActive)
                .filter(o -> !Objects.equals(o.getId(), card.getId()))
                .filter(o -> Objects.equals(o.getDistrictId(), card.getDistrictId()))
                .anyMatch(o -> o.overlaps(from, to));
        if (clash) {
            throw new BusinessRuleException(
                    "Another active %s rate of this partner already covers that district in the same period."
                            .formatted(card.getRateType().name().toLowerCase()));
        }
    }

    private void requireValidSlabs(List<PdpRateSlab> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            PdpRateSlab s = sorted.get(i);
            if (s.getMaxWeight() == null && i < sorted.size() - 1) {
                throw new BusinessRuleException("Only the last weight slab may have no upper limit.");
            }
            if (i > 0 && s.getMinWeight().compareTo(sorted.get(i - 1).getMaxWeight()) < 0) {
                throw new BusinessRuleException("Weight slabs overlap at %s kg."
                        .formatted(s.getMinWeight().stripTrailingZeros().toPlainString()));
            }
        }
    }

    private Branch requirePartner(UUID branchId) {
        if (branchId == null) {
            throw new BusinessRuleException("A partner is required.");
        }
        Branch branch = branchService.getById(branchId);
        if (branch.getBranchType() != BranchType.PICKUP_DELIVERY_PARTNER) {
            throw new BusinessRuleException(
                    "Branch %s is not a Pickup-Delivery Partner.".formatted(branch.getBranchCode()));
        }
        return branch;
    }

    private static PdpRateType requireType(SaveCommand c) {
        if (c.rateType() == null) {
            throw new BusinessRuleException("A rate type (PICKUP or DELIVERY) is required.");
        }
        return c.rateType();
    }

    private PdpRateView view(UUID companyId, PdpRateCard card) {
        rates.flush();
        String code = null;
        String name = null;
        try {
            Branch b = branchService.getById(card.getBranchId());
            code = b.getBranchCode();
            name = b.getBranchName();
        } catch (ResourceNotFoundException ignored) {
            // partner soft-deleted: the card stays readable, just unnamed
        }
        return new PdpRateView(card, code, name, slabs.findByRate(companyId, card.getId()));
    }

    private PdpRateCard load(UUID id, UUID companyId) {
        return rates.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Partner rates belong to a company."));
    }

    private String actor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }
}
