package com.courier.modules.pdp.application;

import com.courier.modules.company.application.UserService;
import com.courier.modules.districtfreight.domain.PincodeCoverageLookupPort;
import com.courier.modules.districtfreight.domain.PincodeCoverageLookupPort.CoverageRef;
import com.courier.modules.pdp.application.PdpRateService.Quote;
import com.courier.modules.pdp.application.PdpRateService.QuoteCommand;
import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpEarningRepository;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdpEarningServiceImpl implements PdpEarningService {

    private static final String ENTITY = "PdpEarning";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final PdpEarningRepository earnings;
    private final PdpRateService rateService;
    private final PincodeCoverageLookupPort coverage;
    private final AuditService auditService;
    private final UserService userService;

    @Override
    @Transactional
    public void recordPickup(UUID branchId, UUID pickupId, String pickupNo, String pincode, BigDecimal weightKg,
                             LocalDate date) {
        record(PdpEarningType.PICKUP, branchId, pickupId, pickupNo, pincode, weightKg, date);
    }

    @Override
    @Transactional
    public void recordDelivery(UUID branchId, UUID shipmentId, String shipmentNo, String pincode, BigDecimal weightKg,
                               LocalDate date) {
        record(PdpEarningType.DELIVERY, branchId, shipmentId, shipmentNo, pincode, weightKg, date);
    }

    @Override
    @Transactional
    public void reverseForShipment(UUID shipmentId, UUID pickupId) {
        UUID companyId = requireCompany();
        if (pickupId != null) {
            earnings.findBySource(companyId, PdpEarningType.PICKUP, pickupId).ifPresent(this::reverse);
        }
        earnings.findBySource(companyId, PdpEarningType.DELIVERY, shipmentId).ifPresent(this::reverse);
    }

    private void reverse(PdpEarning e) {
        if (e.getStatus() == PdpEarningStatus.REVERSED) {
            return;
        }
        boolean billed = e.getStatus() == PdpEarningStatus.BILLED;
        e.setStatus(PdpEarningStatus.REVERSED);
        earnings.save(e);
        if (!billed || e.getTotalAmount().signum() == 0) {
            return;
        }
        // Already on a statement: leave it there, claw it back on the next one with a negative line.
        // Its own id is the adjustment's source id, so the unique (type, source) key keeps this idempotent.
        earnings.save(PdpEarning.builder().branchId(e.getBranchId()).earningType(e.getEarningType()).sourceId(e.getId())
                .referenceNo("REV-" + e.getReferenceNo()).earnedOn(LocalDate.now(java.time.ZoneOffset.UTC))
                .status(PdpEarningStatus.ACCRUED).pincode(e.getPincode()).districtId(e.getDistrictId())
                .weightCharge(e.getWeightCharge().negate()).mathadiCharge(e.getMathadiCharge().negate())
                .odaCharge(e.getOdaCharge().negate()).totalAmount(e.getTotalAmount().negate())
                .remark("Reversal of " + e.getReferenceNo() + " (shipment cancelled)").build());
        log.info("PDP earning {} reversed after billing; adjustment accrued for partner {}", e.getReferenceNo(), e.getBranchId());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<PdpEarning> search(UUID branchId, PdpEarningType type, PdpEarningStatus status, LocalDate from,
                                   LocalDate to, Pageable pageable) {
        UUID own = ownPartnerScope();
        return earnings.search(requireCompany(), own != null ? own : branchId, type, status, from, to, pageable);
    }

    /** The caller's own branch when branch-bound (a partner sees only its own earnings), else null. */
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
    public PdpEarning reprice(UUID earningId) {
        UUID companyId = requireCompany();
        PdpEarning e = earnings.findByIdWithinCompany(earningId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, earningId));
        if (e.getStatus() != PdpEarningStatus.UNRATED) {
            throw new BusinessRuleException("Only an unrated earning can be re-priced.");
        }
        if (!price(e)) {
            throw new BusinessRuleException("Still no active %s rate for this partner and district."
                    .formatted(e.getEarningType().name().toLowerCase()));
        }
        PdpEarning saved = earnings.save(e);
        auditService.record(AuditAction.PDP_EARNING_REPRICED, ENTITY, saved.getId(),
                Map.of("referenceNo", saved.getReferenceNo(), "amount", saved.getTotalAmount().toPlainString()));
        return saved;
    }

    // ------------------------------------------------------------------ helpers

    private void record(PdpEarningType type, UUID branchId, UUID sourceId, String referenceNo, String pincode,
                        BigDecimal weightKg, LocalDate date) {
        UUID companyId = requireCompany();
        if (earnings.findBySource(companyId, type, sourceId).isPresent()) {
            return; // a job is never paid twice
        }
        PdpEarning e = PdpEarning.builder().branchId(branchId).earningType(type).sourceId(sourceId)
                .referenceNo(referenceNo).earnedOn(date == null ? LocalDate.now() : date).pincode(pincode)
                .actualWeightKg(weightKg).status(PdpEarningStatus.UNRATED)
                .remark("No active rate card").build();
        if (price(e)) {
            log.info("PDP {} earning {} for partner {}: {}", type, referenceNo, branchId, e.getTotalAmount());
        } else {
            log.warn("PDP {} earning {} for partner {} left UNRATED — no active rate card", type, referenceNo, branchId);
        }
        earnings.save(e);
    }

    /** Prices {@code e} from the partner's live rate card; false (earning untouched) when none applies. */
    private boolean price(PdpEarning e) {
        // The pincode master is platform-global: the coverage lookup rebinds the company filter for
        // its own query, and Hibernate's auto-flush would then write the caller's still-dirty
        // entities (the pickup / shipment that triggered this) under the wrong company and be
        // blocked. Flush them here, while the request's own company is still bound.
        earnings.flush();
        Optional<CoverageRef> ref = e.getPincode() == null ? Optional.empty() : coverage.findByPincode(e.getPincode());
        UUID district = ref.map(CoverageRef::districtId).orElse(null);
        boolean oda = ref.map(CoverageRef::odaApplicable).orElse(false);
        BigDecimal weight = e.getActualWeightKg() == null || e.getActualWeightKg().signum() <= 0
                ? BigDecimal.ONE : e.getActualWeightKg();
        try {
            Quote q = rateService.quote(new QuoteCommand(e.getBranchId(),
                    PdpRateType.valueOf(e.getEarningType().name()), district, weight, oda, e.getEarnedOn()));
            e.setDistrictId(district);
            e.setRateId(q.rateId());
            e.setChargeableWeightKg(q.chargeableWeight());
            e.setRatePerKg(q.ratePerKg());
            e.setWeightCharge(q.weightCharge());
            e.setMathadiCharge(q.mathadiCharge());
            e.setOdaCharge(q.odaCharge());
            e.setTotalAmount(q.total());
            e.setStatus(PdpEarningStatus.ACCRUED);
            e.setRemark(null);
            return true;
        } catch (BusinessRuleException noCard) {
            e.setDistrictId(district);
            e.setRemark(noCard.getMessage());
            return false;
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Earnings belong to a company."));
    }
}
