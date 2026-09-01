package com.courier.modules.rate.application;

import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.Route;
import com.courier.modules.rate.application.command.CreateRateCommand;
import com.courier.modules.rate.application.command.RateCalculationCommand;
import com.courier.modules.rate.application.command.UpdateRateCommand;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.rate.domain.RateCriteria;
import com.courier.modules.rate.domain.RateRepository;
import com.courier.modules.rate.domain.RateSpecifications;
import com.courier.modules.rate.domain.RateStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Rate use cases. Per-method {@code @PreAuthorize} — see {@link RateService} for the
 * audiences. Company isolation is the project's two layers: the Hibernate filter, plus
 * {@code findByIdWithinCompany} on every single-row load, so a foreign rate id is a 404.
 *
 * <p>{@code routeId}, {@code serviceTypeId}, {@code packageTypeId} and
 * {@code paymentModeId} are validated against {@code com.courier.modules.master}'s own
 * application service interfaces — a forward cross-feature dependency, not a port, the
 * same treatment {@code CustomerAddressServiceImpl} gives the global geography masters.
 * Checking {@link Route#isActive()} on the object {@code RouteService.getById}/
 * {@code findByBranches} legitimately hands back is not a reach into master's domain
 * layer from the outside — it is reading a field off the value that seam was built to
 * return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateServiceImpl implements RateService {

    private static final String ENTITY = "Rate";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";
    private static final int MONEY_SCALE = 2;

    private final RateRepository repository;
    private final RouteService routeService;
    private final ServiceTypeService serviceTypeService;
    private final PackageTypeService packageTypeService;
    private final PaymentModeService paymentModeService;
    private final AuditService auditService;

    // ------------------------------------------------------------------- create

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Rate create(CreateRateCommand command) {
        UUID companyId = requireCompany();

        String code = Rate.normaliseCode(command.rateCode());
        requireCodeAvailable(companyId, code, null);
        requireCombinationValid(command.routeId(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId());

        Rate rate = Rate.builder()
                .rateCode(code)
                .rateName(command.rateName())
                .routeId(command.routeId())
                .serviceTypeId(command.serviceTypeId())
                .packageTypeId(command.packageTypeId())
                .paymentModeId(command.paymentModeId())
                .minimumWeight(command.minimumWeight())
                .maximumWeight(command.maximumWeight())
                .weightUnit(command.weightUnit())
                .baseRate(command.baseRate())
                .additionalWeight(command.additionalWeight())
                .additionalWeightRate(command.additionalWeightRate())
                .minimumCharge(command.minimumCharge())
                .fuelSurcharge(command.fuelSurcharge())
                .handlingCharge(command.handlingCharge())
                .odaCharge(command.odaCharge())
                .insuranceCharge(command.insuranceCharge())
                .gstPercentage(command.gstPercentage())
                .effectiveFrom(command.effectiveFrom())
                .effectiveTo(command.effectiveTo())
                .status(RateStatus.ACTIVE)
                .build();

        rate.applyInvariants();
        // A new rate always starts ACTIVE, so both rules that gate an active rate apply
        // unconditionally here.
        requireActiveRoute(rate.getRouteId(), companyId);
        requireNoOverlap(rate, companyId);

        Rate saved = repository.save(rate);
        log.info("Rate {} ({}) created in company {} by {}",
                saved.getRateCode(), saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.RATE_CREATED, ENTITY, saved.getId(),
                Map.of("rateCode", saved.getRateCode(), "routeId", String.valueOf(saved.getRouteId())));
        return saved;
    }

    // ------------------------------------------------------------------- update

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Rate update(UUID id, UpdateRateCommand command) {
        UUID companyId = requireCompany();
        Rate rate = loadOrThrow(id, companyId);
        requireCurrentVersion(rate, command.expectedVersion());
        requireCombinationValid(command.routeId(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId());

        Map<String, Object> before = snapshot(rate);

        rate.setRateName(command.rateName());
        rate.setRouteId(command.routeId());
        rate.setServiceTypeId(command.serviceTypeId());
        rate.setPackageTypeId(command.packageTypeId());
        rate.setPaymentModeId(command.paymentModeId());
        rate.setMinimumWeight(command.minimumWeight());
        rate.setMaximumWeight(command.maximumWeight());
        rate.setWeightUnit(command.weightUnit());
        rate.setBaseRate(command.baseRate());
        rate.setAdditionalWeight(command.additionalWeight());
        rate.setAdditionalWeightRate(command.additionalWeightRate());
        rate.setMinimumCharge(command.minimumCharge());
        rate.setFuelSurcharge(command.fuelSurcharge());
        rate.setHandlingCharge(command.handlingCharge());
        rate.setOdaCharge(command.odaCharge());
        rate.setInsuranceCharge(command.insuranceCharge());
        rate.setGstPercentage(command.gstPercentage());
        rate.setEffectiveFrom(command.effectiveFrom());
        rate.setEffectiveTo(command.effectiveTo());

        rate.applyInvariants();
        if (rate.isActive()) {
            requireActiveRoute(rate.getRouteId(), companyId);
            requireNoOverlap(rate, companyId);
        }

        Rate saved = repository.save(rate);
        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("Rate {} updated in company {} by {} ({} field(s))",
                saved.getRateCode(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.RATE_UPDATED, ENTITY, saved.getId(), changes);
        return saved;
    }

    // -------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Rate getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<Rate> search(RateCriteria criteria, Pageable pageable) {
        RateCriteria safe = criteria == null ? RateCriteria.none() : criteria;
        return repository.findAll(RateSpecifications.matching(safe), pageable);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Rate activate(UUID id) {
        UUID companyId = requireCompany();
        Rate rate = loadOrThrow(id, companyId);
        if (rate.isActive()) {
            return rate;
        }
        requireActiveRoute(rate.getRouteId(), companyId);
        requireNoOverlap(rate, companyId);
        rate.activate();
        Rate saved = repository.save(rate);
        auditService.record(AuditAction.RATE_ACTIVATED, ENTITY, saved.getId(),
                Map.of("rateCode", saved.getRateCode()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Rate deactivate(UUID id) {
        UUID companyId = requireCompany();
        Rate rate = loadOrThrow(id, companyId);
        if (!rate.isActive()) {
            return rate;
        }
        rate.deactivate();
        Rate saved = repository.save(rate);
        auditService.record(AuditAction.RATE_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("rateCode", saved.getRateCode()));
        return saved;
    }

    // ------------------------------------------------------------------ calculate

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public RateCalculationResult calculate(RateCalculationCommand command) {
        UUID companyId = requireCompany();
        if (command.actualWeight() == null || command.actualWeight().signum() <= 0) {
            throw new BusinessRuleException("Actual weight must be greater than zero.");
        }

        Route route = routeService.findByBranches(command.bookingBranchId(), command.deliveryBranchId());
        if (!route.isActive()) {
            throw new BusinessRuleException(
                    "Route %s is inactive and cannot be priced.".formatted(route.getCode()));
        }
        requireExists(command.serviceTypeId(), serviceTypeService::getById, "service type");
        requireExists(command.packageTypeId(), packageTypeService::getById, "package type");
        requireExists(command.paymentModeId(), paymentModeService::getById, "payment mode");

        LocalDate bookingDate = command.bookingDate() == null ? LocalDate.now() : command.bookingDate();

        List<Rate> candidates = findActiveCandidates(route.getId(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId(), bookingDate);

        if (candidates.isEmpty()) {
            throw new BusinessRuleException(
                    ("No active rate is effective on %s for this route, service type, package "
                            + "type and payment mode.").formatted(bookingDate));
        }

        BigDecimal actualWeight = command.actualWeight();
        Rate exact = candidates.stream().filter(r -> r.covers(actualWeight)).findFirst().orElse(null);

        Rate matched;
        BigDecimal chargeableWeight;
        BigDecimal freight;

        if (exact != null) {
            matched = exact;
            chargeableWeight = actualWeight;
            freight = matched.getBaseRate();
        } else {
            Rate lowest = candidates.stream()
                    .min(Comparator.comparing(Rate::getMinimumWeight)).orElseThrow();
            Rate highest = candidates.stream()
                    .max(Comparator.comparing(Rate::getMaximumWeight)).orElseThrow();

            if (actualWeight.compareTo(lowest.getMinimumWeight()) < 0) {
                matched = lowest;
                chargeableWeight = matched.getMinimumWeight();
                freight = matched.getBaseRate();
            } else if (actualWeight.compareTo(highest.getMaximumWeight()) > 0) {
                matched = highest;
                BigDecimal extra = actualWeight.subtract(matched.getMaximumWeight());
                BigDecimal units = extra.divide(matched.getAdditionalWeight(), 0, RoundingMode.CEILING);
                freight = matched.getBaseRate()
                        .add(units.multiply(matched.getAdditionalWeightRate()));
                chargeableWeight = matched.getMaximumWeight()
                        .add(units.multiply(matched.getAdditionalWeight()));
            } else {
                throw new BusinessRuleException(
                        ("No rate slab covers %s %s for this route, service type, package type "
                                + "and payment mode — there is a gap between the configured slabs.")
                                .formatted(actualWeight, candidates.get(0).getWeightUnit()));
            }
        }

        freight = freight.max(matched.getMinimumCharge());

        BigDecimal subtotal = freight
                .add(matched.getFuelSurcharge())
                .add(matched.getHandlingCharge())
                .add(matched.getOdaCharge())
                .add(matched.getInsuranceCharge());
        BigDecimal gstAmount = subtotal
                .multiply(matched.getGstPercentage())
                .divide(new BigDecimal(100), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(gstAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new RateCalculationResult(
                matched,
                chargeableWeight,
                freight.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                matched.getFuelSurcharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                matched.getHandlingCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                matched.getOdaCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                matched.getInsuranceCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                gstAmount,
                totalAmount);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<Rate> findActiveCandidates(UUID routeId, UUID serviceTypeId, UUID packageTypeId,
                                           UUID paymentModeId, LocalDate bookingDate) {
        UUID companyId = requireCompany();
        LocalDate effectiveDate = bookingDate == null ? LocalDate.now() : bookingDate;
        return repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        companyId, routeId, serviceTypeId, packageTypeId, paymentModeId,
                        RateStatus.ACTIVE)
                .stream()
                .filter(r -> r.coversDate(effectiveDate))
                .toList();
    }

    // -------------------------------------------------------------------- helpers

    Rate loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireCodeAvailable(UUID companyId, String code, UUID excludeId) {
        if (repository.isCodeTaken(companyId, code, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "rateCode", code);
        }
    }

    private void requireCombinationValid(UUID routeId, UUID serviceTypeId, UUID packageTypeId,
                                         UUID paymentModeId) {
        requireExists(routeId, routeService::getById, "route");
        requireExists(serviceTypeId, serviceTypeService::getById, "service type");
        requireExists(packageTypeId, packageTypeService::getById, "package type");
        requireExists(paymentModeId, paymentModeService::getById, "payment mode");
    }

    private void requireExists(UUID id, Function<UUID, ?> lookup, String label) {
        if (id == null) {
            throw new BusinessRuleException("A " + label + " is required.");
        }
        try {
            lookup.apply(id);
        } catch (ResourceNotFoundException e) {
            throw new BusinessRuleException("No such " + label + ": " + id);
        }
    }

    /** Only Active Routes can have active Rates. */
    private void requireActiveRoute(UUID routeId, UUID companyId) {
        Route route = routeService.getById(routeId);
        if (!route.isActive()) {
            throw new BusinessRuleException(
                    "Route %s is inactive. Only an active route may have an active rate."
                            .formatted(route.getCode()));
        }
    }

    /**
     * No two ACTIVE rates for the same Route + Service Type + Package Type + Payment Mode
     * may cover the same weight. MySQL has no exclusion constraint, so this runs in the
     * service — on save and on activation, or "deactivate, add an overlapping slab,
     * reactivate" walks straight around it, the same reasoning
     * {@code WeightSlabServiceImpl.requireNoOverlap} documents.
     */
    private void requireNoOverlap(Rate candidate, UUID companyId) {
        List<Rate> existing = repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        companyId, candidate.getRouteId(), candidate.getServiceTypeId(),
                        candidate.getPackageTypeId(), candidate.getPaymentModeId(), RateStatus.ACTIVE);

        for (Rate other : existing) {
            if (Objects.equals(other.getId(), candidate.getId())) {
                continue;
            }
            if (candidate.overlapsWeightRange(other)) {
                throw new BusinessRuleException(
                        ("Weight slab %s-%s %s overlaps rate %s (%s-%s) for the same route, "
                                + "service type, package type and payment mode. Slabs are "
                                + "[min, max), so they may touch but not overlap.")
                                .formatted(candidate.getMinimumWeight(), candidate.getMaximumWeight(),
                                        candidate.getWeightUnit(), other.getRateCode(),
                                        other.getMinimumWeight(), other.getMaximumWeight()));
            }
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Rates belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(Rate rate, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(rate.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Rate.class, rate.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }

    private Map<String, Object> snapshot(Rate r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("rateName", r.getRateName());
        v.put("routeId", String.valueOf(r.getRouteId()));
        v.put("serviceTypeId", String.valueOf(r.getServiceTypeId()));
        v.put("packageTypeId", String.valueOf(r.getPackageTypeId()));
        v.put("paymentModeId", String.valueOf(r.getPaymentModeId()));
        v.put("minimumWeight", String.valueOf(r.getMinimumWeight()));
        v.put("maximumWeight", String.valueOf(r.getMaximumWeight()));
        v.put("weightUnit", r.getWeightUnit() == null ? null : r.getWeightUnit().name());
        v.put("baseRate", String.valueOf(r.getBaseRate()));
        v.put("additionalWeight", String.valueOf(r.getAdditionalWeight()));
        v.put("additionalWeightRate", String.valueOf(r.getAdditionalWeightRate()));
        v.put("minimumCharge", String.valueOf(r.getMinimumCharge()));
        v.put("fuelSurcharge", String.valueOf(r.getFuelSurcharge()));
        v.put("handlingCharge", String.valueOf(r.getHandlingCharge()));
        v.put("odaCharge", String.valueOf(r.getOdaCharge()));
        v.put("insuranceCharge", String.valueOf(r.getInsuranceCharge()));
        v.put("gstPercentage", String.valueOf(r.getGstPercentage()));
        v.put("effectiveFrom", String.valueOf(r.getEffectiveFrom()));
        v.put("effectiveTo", String.valueOf(r.getEffectiveTo()));
        return v;
    }

    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object previous = before.get(entry.getKey());
            if (!Objects.equals(previous, entry.getValue())) {
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        return changes;
    }
}
