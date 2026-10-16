package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotRepository;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;
import com.courier.modules.busdepot.domain.BusDivision;
import com.courier.modules.busdepot.domain.BusDivisionRepository;
import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.domain.Branch;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusDepotServiceImpl implements BusDepotService {

    private static final String ENTITY = "BusDepot";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final BusDepotRepository depots;
    private final BusDivisionRepository divisions;
    private final BranchService branchService;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public BusDepotView create(DepotInput in) {
        UUID companyId = requireCompany();
        BusDepot depot = BusDepot.builder().status(BusDepotStatus.ACTIVE).build();
        apply(depot, in);
        requireUnique(companyId, depot, null);
        requireBranch(depot.getBranchId());
        requireStructure(companyId, depot);
        BusDepot saved = depots.save(depot);
        log.info("Bus depot {} ({}) created in company {} by {}", saved.getDepotCode(), saved.getId(), companyId, actor());
        auditService.record(AuditAction.BUS_DEPOT_CREATED, ENTITY, saved.getId(),
                Map.of("depotCode", saved.getDepotCode(), "depotName", saved.getDepotName()));
        return view(saved);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public BusDepotView update(UUID id, DepotInput in, Long expectedVersion) {
        UUID companyId = requireCompany();
        BusDepot depot = load(id, companyId);
        if (expectedVersion != null && !Objects.equals(depot.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(BusDepot.class, depot.getId());
        }
        UUID previousBranch = depot.getBranchId();
        apply(depot, in);
        requireUnique(companyId, depot, depot.getId());
        // A branch already on the depot is kept even if it was deactivated since; only a
        // newly chosen one must be an active branch.
        if (!Objects.equals(previousBranch, depot.getBranchId())) {
            requireBranch(depot.getBranchId());
        }
        requireStructure(companyId, depot);
        BusDepot saved = depots.save(depot);
        auditService.record(AuditAction.BUS_DEPOT_UPDATED, ENTITY, saved.getId(),
                Map.of("depotCode", saved.getDepotCode(), "depotName", saved.getDepotName()));
        return view(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public BusDepotView getById(UUID id) {
        return view(load(id, requireCompany()));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<BusDepotView> search(BusDepotStatus status, BusDepotType type, UUID branchId, UUID divisionId, String search) {
        UUID companyId = requireCompany();
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        List<BusDepot> found = depots.search(companyId, status, type, branchId, divisionId, pattern);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Branch> branches = branchService.listActiveDirectory().stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (a, b) -> a));
        Map<UUID, String> divisionNames = divisions.search(companyId, null, null).stream()
                .collect(Collectors.toMap(BusDivision::getId, BusDivision::getDivisionName, (a, b) -> a));
        Map<UUID, String> depotNames = depots.search(companyId, null, BusDepotType.BUS_DEPOT, null, null, null).stream()
                .collect(Collectors.toMap(BusDepot::getId, BusDepot::getDepotName, (a, b) -> a));
        return found.stream().map(d -> view(d, branches.get(d.getBranchId()),
                divisionNames.get(d.getDivisionId()), depotNames.get(d.getParentDepotId()))).toList();
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public BusDepotView activate(UUID id) {
        BusDepot depot = load(id, requireCompany());
        depot.activate();
        BusDepot saved = depots.save(depot);
        // Audit after save: recording switches the company binding, which would otherwise
        // flush this dirty row under the wrong company and trip the isolation guard.
        auditService.record(AuditAction.BUS_DEPOT_ACTIVATED, ENTITY, saved.getId(), Map.of("depotCode", saved.getDepotCode()));
        return view(saved);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public BusDepotView deactivate(UUID id) {
        BusDepot depot = load(id, requireCompany());
        depot.deactivate();
        BusDepot saved = depots.save(depot);
        auditService.record(AuditAction.BUS_DEPOT_DEACTIVATED, ENTITY, saved.getId(), Map.of("depotCode", saved.getDepotCode()));
        return view(saved);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        BusDepot depot = load(id, companyId);
        if (depots.existsByParent(companyId, depot.getId())) {
            throw new BusinessRuleException("Stations are still served by this depot. Reassign or delete them first.");
        }
        depot.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        depots.save(depot);
        log.warn("Bus depot {} deleted by {}", depot.getId(), actor());
        auditService.record(AuditAction.BUS_DEPOT_DELETED, ENTITY, depot.getId(), Map.of("depotCode", depot.getDepotCode()));
    }

    // ------------------------------------------------------------------ helpers

    private void apply(BusDepot d, DepotInput in) {
        d.setDepotCode(in.depotCode());
        d.setDepotName(in.depotName());
        d.setDepotType(in.depotType());
        d.setBranchId(in.branchId());
        d.setDivisionId(in.divisionId());
        d.setParentDepotId(in.parentDepotId());
        d.setAddress(in.address());
        d.setCity(in.city());
        d.setDistrict(in.district());
        d.setState(in.state());
        d.setPincode(in.pincode());
        d.setContactPerson(in.contactPerson());
        d.setContactMobile(in.contactMobile());
        d.applyInvariants();
    }

    private void requireUnique(UUID companyId, BusDepot d, UUID excludeId) {
        if (depots.existsByCode(companyId, d.getDepotCode(), excludeId)) {
            throw new DuplicateResourceException(ENTITY, "depotCode", d.getDepotCode());
        }
        if (depots.existsByName(companyId, d.getDepotName(), excludeId)) {
            throw new DuplicateResourceException(ENTITY, "depotName", d.getDepotName());
        }
    }

    /** The assigned branch/vendor must be an active branch of this company; null is fine. */
    private void requireBranch(UUID branchId) {
        if (branchId == null) {
            return;
        }
        boolean ok = branchService.listActiveDirectory().stream().anyMatch(b -> b.getId().equals(branchId));
        if (!ok) {
            throw new BusinessRuleException("The chosen branch / vendor is not an active branch of your company.");
        }
    }

    /** Division must exist; a serving depot must be a different BUS_DEPOT of the same division, and only a station has one. */
    private void requireStructure(UUID companyId, BusDepot d) {
        if (d.getDivisionId() != null && divisions.findByIdWithinCompany(d.getDivisionId(), companyId).isEmpty()) {
            throw new BusinessRuleException("The chosen division does not exist in your company.");
        }
        if (d.getParentDepotId() == null) {
            return;
        }
        if (d.getDepotType() != BusDepotType.BUS_STATION) {
            throw new BusinessRuleException("Only a bus station can be served by a depot.");
        }
        BusDepot parent = depots.findByIdWithinCompany(d.getParentDepotId(), companyId)
                .orElseThrow(() -> new BusinessRuleException("The serving depot does not exist in your company."));
        if (parent.getDepotType() != BusDepotType.BUS_DEPOT) {
            throw new BusinessRuleException("The serving depot must be a bus depot, not a station.");
        }
        if (!Objects.equals(parent.getDivisionId(), d.getDivisionId())) {
            throw new BusinessRuleException("The serving depot must belong to the same division.");
        }
    }

    private BusDepotView view(BusDepot d) {
        Branch branch = d.getBranchId() == null ? null : branchService.listActiveDirectory().stream()
                .filter(b -> b.getId().equals(d.getBranchId())).findFirst().orElse(null);
        String divisionName = d.getDivisionId() == null ? null : divisions.findByIdWithinCompany(d.getDivisionId(), d.getCompanyId())
                .map(BusDivision::getDivisionName).orElse(null);
        String parentName = d.getParentDepotId() == null ? null : depots.findByIdWithinCompany(d.getParentDepotId(), d.getCompanyId())
                .map(BusDepot::getDepotName).orElse(null);
        return view(d, branch, divisionName, parentName);
    }

    private BusDepotView view(BusDepot d, Branch b, String divisionName, String parentDepotName) {
        return new BusDepotView(d, b == null ? null : b.getBranchCode(), b == null ? null : b.getBranchName(),
                b == null || b.getBranchType() == null ? null : b.getBranchType().name(), divisionName, parentDepotName);
    }

    private BusDepot load(UUID id, UUID companyId) {
        return depots.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Bus depots belong to a company."));
    }

    private String actor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }
}
