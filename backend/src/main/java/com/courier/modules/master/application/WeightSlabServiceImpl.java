package com.courier.modules.master.application;

import com.courier.modules.master.application.command.WeightSlabCommand;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.WeightSlab;
import com.courier.modules.master.domain.WeightSlabRepository;
import com.courier.modules.master.domain.WeightUnit;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Weight slabs.
 *
 * <p>The rule with teeth is the overlap check: no two <b>active</b> slabs of the same unit
 * may share a weight. Without it, "which slab does 2 kg fall in" has two answers, the rate
 * engine picks whichever the database returned first, and two identical shipments get
 * different prices — a bug that shows up as a customer complaint months later, not as an
 * error.
 *
 * <p>It is checked in the service and not the database because MySQL has no exclusion
 * constraint. Inactive and soft-deleted slabs are excluded on purpose: a superseded tariff
 * has to be allowed to sit alongside the one that replaced it.
 *
 * <p>The check runs on activation too. Deactivating a slab, adding an overlapping one and
 * then reactivating the first would otherwise walk straight around it.
 */
@Slf4j
@Service
public class WeightSlabServiceImpl extends AbstractMasterDataService<WeightSlab>
        implements WeightSlabService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final WeightSlabRepository slabs;

    public WeightSlabServiceImpl(WeightSlabRepository slabs,
                                 MasterUniquenessChecker uniqueness,
                                 AuditService auditService) {
        super(slabs, uniqueness, auditService, "Weight slab", MasterTable.WEIGHT_SLABS);
        this.slabs = slabs;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public WeightSlab create(WeightSlabCommand command) {
        WeightSlab slab = new WeightSlab();
        applyCommonFields(slab, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(slab, command);
        return createEntity(slab);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public WeightSlab update(UUID id, WeightSlabCommand command) {
        return updateEntity(id, command.expectedVersion(), slab -> {
            applyCommonFields(slab, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(slab, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public WeightSlab getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<WeightSlab> search(MasterDataCriteria criteria, Pageable pageable) {
        return doSearch(criteria, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        doDelete(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public WeightSlab activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public WeightSlab deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(WeightSlab slab, UUID companyId, UUID excludeId) {
        requireAvailable(companyId, excludeId, Map.of("name", slab.getName()),
                "name", slab.getName());
        if (slab.isActive()) {
            requireNoOverlap(slab, companyId);
        }
    }

    @Override
    protected void requireActivatable(WeightSlab slab, UUID companyId) {
        requireNoOverlap(slab, companyId);
    }

    @Override
    protected Map<String, Object> snapshot(WeightSlab slab) {
        Map<String, Object> values = super.snapshot(slab);
        values.put("minWeight", String.valueOf(slab.getMinWeight()));
        values.put("maxWeight", String.valueOf(slab.getMaxWeight()));
        values.put("weightUnit", slab.getWeightUnit() == null ? null : slab.getWeightUnit().name());
        return values;
    }

    private void requireNoOverlap(WeightSlab candidate, UUID companyId) {
        List<WeightSlab> existing = slabs.findByCompanyIdAndWeightUnitAndStatus(
                companyId, candidate.getWeightUnit(), MasterStatus.ACTIVE);

        for (WeightSlab other : existing) {
            if (Objects.equals(other.getId(), candidate.getId())) {
                continue;
            }
            if (candidate.overlaps(other)) {
                throw new BusinessRuleException(
                        ("Weight slab %s-%s %s overlaps %s (%s-%s). Slabs are [min, max), so "
                                + "they may touch but not overlap.")
                                .formatted(candidate.getMinWeight(), candidate.getMaxWeight(),
                                        candidate.getWeightUnit(), other.getCode(),
                                        other.getMinWeight(), other.getMaxWeight()));
            }
        }
    }

    private void applySpecific(WeightSlab slab, WeightSlabCommand command) {
        slab.setMinWeight(command.minWeight());
        slab.setMaxWeight(command.maxWeight());
        slab.setWeightUnit(command.weightUnit() == null ? WeightUnit.KG : command.weightUnit());
    }
}
