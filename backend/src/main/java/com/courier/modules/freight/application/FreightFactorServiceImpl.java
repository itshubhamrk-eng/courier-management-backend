package com.courier.modules.freight.application;

import com.courier.modules.distance.application.AddressDistanceService;
import com.courier.modules.distance.domain.AddressDistance;
import com.courier.modules.freight.application.command.CreateFreightFactorCommand;
import com.courier.modules.freight.application.command.FreightCalculationCommand;
import com.courier.modules.freight.application.command.UpdateFreightFactorCommand;
import com.courier.modules.freight.domain.FreightFactor;
import com.courier.modules.freight.domain.FreightFactorCriteria;
import com.courier.modules.freight.domain.FreightFactorRepository;
import com.courier.modules.freight.domain.FreightFactorSpecifications;
import com.courier.modules.freight.domain.FreightFactorStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Freight factor use cases. Per-method {@code @PreAuthorize} — see
 * {@link FreightFactorService} for the audiences. Company isolation is the project's two
 * layers: the Hibernate filter, plus {@code findByIdWithinCompany} on every single-row
 * load, so a foreign id is a 404.
 *
 * <p>Deliberately independent of {@code com.courier.modules.rate}/{@code .pricing} — the
 * one forward cross-feature dependency here is {@link AddressDistanceService}, to resolve
 * a branch pair's road distance for {@link #calculate}, the same "forward dependency
 * through an application service interface, not a port" pattern {@code RateServiceImpl}
 * uses on {@code modules.master}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreightFactorServiceImpl implements FreightFactorService {

    private static final String ENTITY = "FreightFactor";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";
    private static final int MONEY_SCALE = 2;

    private final FreightFactorRepository repository;
    private final AddressDistanceService addressDistanceService;
    private final AuditService auditService;

    // ------------------------------------------------------------------- create

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public FreightFactor create(CreateFreightFactorCommand command) {
        UUID companyId = requireCompany();

        FreightFactor factor = FreightFactor.builder()
                .fromKm(command.fromKm())
                .toKm(command.toKm())
                .fromWeight(command.fromWeight())
                .toWeight(command.toWeight())
                .factor(command.factor())
                .status(FreightFactorStatus.ACTIVE)
                .build();

        factor.applyInvariants();
        // A new cell always starts ACTIVE, so the overlap rule applies unconditionally.
        requireNoOverlap(factor, companyId);

        FreightFactor saved = repository.save(factor);
        log.info("Freight factor {} ({}-{} km, {}-{} kg) created in company {} by {}",
                saved.getId(), saved.getFromKm(), saved.getToKm(),
                saved.getFromWeight(), saved.getToWeight(), companyId, currentActor());
        auditService.record(AuditAction.FREIGHT_FACTOR_CREATED, ENTITY, saved.getId(),
                Map.of("fromKm", String.valueOf(saved.getFromKm()), "toKm", String.valueOf(saved.getToKm()),
                        "fromWeight", String.valueOf(saved.getFromWeight()),
                        "toWeight", String.valueOf(saved.getToWeight())));
        return saved;
    }

    // ------------------------------------------------------------------- update

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public FreightFactor update(UUID id, UpdateFreightFactorCommand command) {
        UUID companyId = requireCompany();
        FreightFactor factor = loadOrThrow(id, companyId);
        requireCurrentVersion(factor, command.expectedVersion());

        Map<String, Object> before = snapshot(factor);

        factor.setFromKm(command.fromKm());
        factor.setToKm(command.toKm());
        factor.setFromWeight(command.fromWeight());
        factor.setToWeight(command.toWeight());
        factor.setFactor(command.factor());

        factor.applyInvariants();
        if (factor.isActive()) {
            requireNoOverlap(factor, companyId);
        }

        FreightFactor saved = repository.save(factor);
        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("Freight factor {} updated in company {} by {} ({} field(s))",
                saved.getId(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.FREIGHT_FACTOR_UPDATED, ENTITY, saved.getId(), changes);
        return saved;
    }

    // -------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public FreightFactor getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<FreightFactor> search(FreightFactorCriteria criteria, Pageable pageable) {
        FreightFactorCriteria safe = criteria == null ? FreightFactorCriteria.none() : criteria;
        return repository.findAll(FreightFactorSpecifications.matching(safe), pageable);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public FreightFactor activate(UUID id) {
        UUID companyId = requireCompany();
        FreightFactor factor = loadOrThrow(id, companyId);
        if (factor.isActive()) {
            return factor;
        }
        requireNoOverlap(factor, companyId);
        factor.activate();
        FreightFactor saved = repository.save(factor);
        auditService.record(AuditAction.FREIGHT_FACTOR_ACTIVATED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public FreightFactor deactivate(UUID id) {
        UUID companyId = requireCompany();
        FreightFactor factor = loadOrThrow(id, companyId);
        if (!factor.isActive()) {
            return factor;
        }
        factor.deactivate();
        FreightFactor saved = repository.save(factor);
        auditService.record(AuditAction.FREIGHT_FACTOR_DEACTIVATED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    // ------------------------------------------------------------------ calculate

    @Override
    @Transactional
    @PreAuthorize(READ)
    public FreightCalculationResult calculate(FreightCalculationCommand command) {
        UUID companyId = requireCompany();
        if (command.weight() == null || command.weight().signum() <= 0) {
            throw new BusinessRuleException("Weight must be greater than zero.");
        }

        AddressDistance distance = addressDistanceService
                .resolveBranchDistance(command.fromBranchId(), command.toBranchId());
        BigDecimal distanceKm = distance.getDistanceKm();
        BigDecimal weight = command.weight();

        List<FreightFactor> candidates = repository.findByCompanyIdAndStatus(companyId, FreightFactorStatus.ACTIVE);
        FreightFactor matched = candidates.stream()
                .filter(f -> f.coversDistance(distanceKm) && f.coversWeight(weight))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        ("No freight factor slab covers %s km / %s kg — there is a gap in the "
                                + "configured grid.").formatted(distanceKm, weight)));

        BigDecimal freight = matched.getFactor().multiply(weight).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new FreightCalculationResult(matched, distanceKm, weight, freight);
    }

    // -------------------------------------------------------------------- helpers

    FreightFactor loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * No two ACTIVE cells may cover the same (distance, weight) point. MySQL has no
     * exclusion constraint, so this runs in the service — on save and on activation, or
     * "deactivate, add an overlapping cell, reactivate" walks straight around it, the same
     * reasoning {@code RateServiceImpl.requireNoOverlap} documents.
     */
    private void requireNoOverlap(FreightFactor candidate, UUID companyId) {
        List<FreightFactor> existing = repository.findByCompanyIdAndStatus(companyId, FreightFactorStatus.ACTIVE);

        for (FreightFactor other : existing) {
            if (Objects.equals(other.getId(), candidate.getId())) {
                continue;
            }
            if (candidate.overlaps(other)) {
                throw new BusinessRuleException(
                        ("Range %s-%s km / %s-%s kg overlaps an existing freight factor "
                                + "(%s-%s km / %s-%s kg). Ranges are [from, to), so they may "
                                + "touch but not overlap.")
                                .formatted(candidate.getFromKm(), candidate.getToKm(),
                                        candidate.getFromWeight(), candidate.getToWeight(),
                                        other.getFromKm(), other.getToKm(),
                                        other.getFromWeight(), other.getToWeight()));
            }
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Freight factors belong to a company, "
                        + "so this operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(FreightFactor factor, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(factor.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(FreightFactor.class, factor.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }

    private Map<String, Object> snapshot(FreightFactor f) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fromKm", String.valueOf(f.getFromKm()));
        v.put("toKm", String.valueOf(f.getToKm()));
        v.put("fromWeight", String.valueOf(f.getFromWeight()));
        v.put("toWeight", String.valueOf(f.getToWeight()));
        v.put("factor", String.valueOf(f.getFactor()));
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
