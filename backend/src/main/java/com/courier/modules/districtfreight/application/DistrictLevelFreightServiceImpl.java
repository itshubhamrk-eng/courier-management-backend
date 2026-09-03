package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.application.command.CreateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.application.command.UpdateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.domain.BranchLookupPort;
import com.courier.modules.districtfreight.domain.DistrictFreightStatus;
import com.courier.modules.districtfreight.domain.DistrictLevelFreight;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightCriteria;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightRepository;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightSpecifications;
import com.courier.modules.districtfreight.domain.DistrictLookupPort;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * District Level Freight use cases. Company isolation is the project's two layers: the
 * Hibernate filter, plus {@code findByIdWithinCompany} on every single-row load, so a
 * foreign id is a 404 — same shape {@code RateServiceImpl} documents.
 *
 * <p>{@code branchId} is validated through {@link BranchLookupPort} (company-owned, this
 * module's own seam onto {@code modules/company}); {@code districtId} through
 * {@link DistrictLookupPort} (the global master, this module's own seam onto
 * {@code modules/master}). Neither entity is imported directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictLevelFreightServiceImpl implements DistrictLevelFreightService {

    private static final String ENTITY = "DistrictLevelFreight";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final DistrictLevelFreightRepository repository;
    private final BranchLookupPort branchLookup;
    private final DistrictLookupPort districtLookup;
    private final AuditService auditService;

    // ------------------------------------------------------------------- create

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DistrictLevelFreight create(CreateDistrictLevelFreightCommand command) {
        UUID companyId = requireCompany();

        BranchLookupPort.BranchRef branch = requireBranch(command.branchId(), companyId);
        DistrictLookupPort.DistrictRef district = requireDistrict(command.districtId());
        requireComboAvailable(companyId, branch.branchId(), district.districtId(), null);

        DistrictLevelFreight freight = new DistrictLevelFreight();
        freight.setBranchId(branch.branchId());
        freight.setDistrictId(district.districtId());
        applyRates(freight, command.rate1To15(), command.rate16To50(), command.rate51To100(),
                command.rate101To1000(), command.rate1001To1500(), command.rate1501To2000(),
                command.odaApplicable(), command.odaCharge());
        freight.setStatus(DistrictFreightStatus.ACTIVE);
        freight.applyInvariants();

        DistrictLevelFreight saved = repository.save(freight);
        log.info("District Level Freight {} -> {} created in company {} by {}",
                branch.branchCode(), district.code(), companyId, currentActor());
        auditService.record(AuditAction.DISTRICT_FREIGHT_CREATED, ENTITY, saved.getId(),
                Map.of("branchCode", branch.branchCode(), "districtCode", district.code()));
        return saved;
    }

    // ------------------------------------------------------------------- update

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DistrictLevelFreight update(UUID id, UpdateDistrictLevelFreightCommand command) {
        UUID companyId = requireCompany();
        DistrictLevelFreight freight = loadOrThrow(id, companyId);
        requireCurrentVersion(freight, command.expectedVersion());

        BranchLookupPort.BranchRef branch = requireBranch(command.branchId(), companyId);
        DistrictLookupPort.DistrictRef district = requireDistrict(command.districtId());
        requireComboAvailable(companyId, branch.branchId(), district.districtId(), freight.getId());

        Map<String, Object> before = snapshot(freight);

        freight.setBranchId(branch.branchId());
        freight.setDistrictId(district.districtId());
        applyRates(freight, command.rate1To15(), command.rate16To50(), command.rate51To100(),
                command.rate101To1000(), command.rate1001To1500(), command.rate1501To2000(),
                command.odaApplicable(), command.odaCharge());
        freight.applyInvariants();

        DistrictLevelFreight saved = repository.save(freight);
        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("District Level Freight {} updated in company {} by {} ({} field(s))",
                saved.getId(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.DISTRICT_FREIGHT_UPDATED, ENTITY, saved.getId(), changes);
        return saved;
    }

    // -------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public DistrictLevelFreight getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<DistrictLevelFreight> search(DistrictLevelFreightCriteria criteria, Pageable pageable) {
        DistrictLevelFreightCriteria safe = criteria == null ? DistrictLevelFreightCriteria.none() : criteria;
        return repository.findAll(DistrictLevelFreightSpecifications.matching(safe), pageable);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        DistrictLevelFreight freight = loadOrThrow(id, companyId);
        freight.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(freight);
        log.info("District Level Freight {} deleted in company {} by {}", id, companyId, currentActor());
        auditService.record(AuditAction.DISTRICT_FREIGHT_DELETED, ENTITY, id, Map.of());
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DistrictLevelFreight activate(UUID id) {
        UUID companyId = requireCompany();
        DistrictLevelFreight freight = loadOrThrow(id, companyId);
        if (freight.isActive()) {
            return freight;
        }
        freight.activate();
        DistrictLevelFreight saved = repository.save(freight);
        auditService.record(AuditAction.DISTRICT_FREIGHT_STATUS_CHANGED, ENTITY, saved.getId(),
                Map.of("status", "ACTIVE"));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DistrictLevelFreight deactivate(UUID id) {
        UUID companyId = requireCompany();
        DistrictLevelFreight freight = loadOrThrow(id, companyId);
        if (!freight.isActive()) {
            return freight;
        }
        freight.deactivate();
        DistrictLevelFreight saved = repository.save(freight);
        auditService.record(AuditAction.DISTRICT_FREIGHT_STATUS_CHANGED, ENTITY, saved.getId(),
                Map.of("status", "INACTIVE"));
        return saved;
    }

    // -------------------------------------------------------------------- helpers

    DistrictLevelFreight loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private BranchLookupPort.BranchRef requireBranch(UUID branchId, UUID companyId) {
        if (branchId == null) {
            throw new BusinessRuleException("A From Station (branch) is required.");
        }
        BranchLookupPort.BranchRef branch = branchLookup.findBranch(branchId, companyId)
                .orElseThrow(() -> new BusinessRuleException("No such branch: " + branchId));
        if (!branch.active()) {
            throw new BusinessRuleException(
                    "Branch %s is inactive and cannot be used as a From Station.".formatted(branch.branchCode()));
        }
        return branch;
    }

    private DistrictLookupPort.DistrictRef requireDistrict(UUID districtId) {
        if (districtId == null) {
            throw new BusinessRuleException("A destination district is required.");
        }
        DistrictLookupPort.DistrictRef district = districtLookup.findDistrict(districtId)
                .orElseThrow(() -> new BusinessRuleException("No such district: " + districtId));
        if (!district.active()) {
            throw new BusinessRuleException(
                    "District %s is inactive and cannot be used as a destination.".formatted(district.code()));
        }
        return district;
    }

    private void requireComboAvailable(UUID companyId, UUID branchId, UUID districtId, UUID excludeId) {
        if (repository.isComboTaken(companyId, branchId, districtId, excludeId)) {
            throw new DuplicateResourceException(
                    "A District Level Freight rate for this From Station and District already exists.");
        }
    }

    private void applyRates(DistrictLevelFreight freight,
                             BigDecimal r1To15, BigDecimal r16To50, BigDecimal r51To100,
                             BigDecimal r101To1000, BigDecimal r1001To1500, BigDecimal r1501To2000,
                             Boolean odaApplicable, BigDecimal odaCharge) {
        freight.setRate1To15(r1To15);
        freight.setRate16To50(r16To50);
        freight.setRate51To100(r51To100);
        freight.setRate101To1000(r101To1000);
        freight.setRate1001To1500(r1001To1500);
        freight.setRate1501To2000(r1501To2000);
        freight.setOdaApplicable(odaApplicable == null || odaApplicable);
        freight.setOdaCharge(odaCharge == null ? new BigDecimal("250.0000") : odaCharge);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. District Level Freight rows belong to a "
                        + "company, so this operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(DistrictLevelFreight freight, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(freight.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(DistrictLevelFreight.class, freight.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }

    private Map<String, Object> snapshot(DistrictLevelFreight f) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("branchId", String.valueOf(f.getBranchId()));
        v.put("districtId", String.valueOf(f.getDistrictId()));
        v.put("rate1To15", String.valueOf(f.getRate1To15()));
        v.put("rate16To50", String.valueOf(f.getRate16To50()));
        v.put("rate51To100", String.valueOf(f.getRate51To100()));
        v.put("rate101To1000", String.valueOf(f.getRate101To1000()));
        v.put("rate1001To1500", String.valueOf(f.getRate1001To1500()));
        v.put("rate1501To2000", String.valueOf(f.getRate1501To2000()));
        v.put("odaApplicable", String.valueOf(f.isOdaApplicable()));
        v.put("odaCharge", String.valueOf(f.getOdaCharge()));
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
