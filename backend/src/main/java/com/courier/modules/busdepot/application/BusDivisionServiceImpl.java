package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotRepository;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;
import com.courier.modules.busdepot.domain.BusDivision;
import com.courier.modules.busdepot.domain.BusDivisionRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusDivisionServiceImpl implements BusDivisionService {

    private static final String ENTITY = "BusDivision";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final BusDivisionRepository divisions;
    private final BusDepotRepository depots;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DivisionView create(DivisionInput in) {
        UUID companyId = requireCompany();
        BusDivision v = BusDivision.builder().build();
        v.setDivisionCode(in.divisionCode());
        v.setDivisionName(in.divisionName());
        v.applyInvariants();
        requireUnique(companyId, v, null);
        BusDivision saved = divisions.save(v);
        auditService.record(AuditAction.BUS_DIVISION_CREATED, ENTITY, saved.getId(),
                Map.of("divisionCode", saved.getDivisionCode(), "divisionName", saved.getDivisionName()));
        return view(saved, companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DivisionView update(UUID id, DivisionInput in, Long expectedVersion) {
        UUID companyId = requireCompany();
        BusDivision v = load(id, companyId);
        if (expectedVersion != null && !Objects.equals(v.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(BusDivision.class, v.getId());
        }
        v.setDivisionCode(in.divisionCode());
        v.setDivisionName(in.divisionName());
        v.applyInvariants();
        requireUnique(companyId, v, v.getId());
        BusDivision saved = divisions.save(v);
        auditService.record(AuditAction.BUS_DIVISION_UPDATED, ENTITY, saved.getId(),
                Map.of("divisionCode", saved.getDivisionCode(), "divisionName", saved.getDivisionName()));
        return view(saved, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public DivisionView getById(UUID id) {
        UUID companyId = requireCompany();
        return view(load(id, companyId), companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<DivisionView> search(BusDepotStatus status, String search) {
        UUID companyId = requireCompany();
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        List<BusDivision> found = divisions.search(companyId, status, pattern);
        Map<UUID, List<BusDepot>> byDivision = depots.search(companyId, null, null, null, null, null).stream()
                .filter(d -> d.getDivisionId() != null)
                .collect(Collectors.groupingBy(BusDepot::getDivisionId));
        return found.stream().map(v -> {
            List<BusDepot> mine = byDivision.getOrDefault(v.getId(), List.of());
            long depotsN = mine.stream().filter(d -> d.getDepotType() == BusDepotType.BUS_DEPOT).count();
            return new DivisionView(v, depotsN, mine.size() - depotsN);
        }).toList();
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DivisionView activate(UUID id) {
        UUID companyId = requireCompany();
        BusDivision v = load(id, companyId);
        v.activate();
        BusDivision saved = divisions.save(v);
        auditService.record(AuditAction.BUS_DIVISION_ACTIVATED, ENTITY, saved.getId(), Map.of("divisionCode", saved.getDivisionCode()));
        return view(saved, companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public DivisionView deactivate(UUID id) {
        UUID companyId = requireCompany();
        BusDivision v = load(id, companyId);
        v.deactivate();
        BusDivision saved = divisions.save(v);
        auditService.record(AuditAction.BUS_DIVISION_DEACTIVATED, ENTITY, saved.getId(), Map.of("divisionCode", saved.getDivisionCode()));
        return view(saved, companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        BusDivision v = load(id, companyId);
        if (depots.existsByDivision(companyId, v.getId())) {
            throw new BusinessRuleException("This division still has depots or stations. Move or delete them first.");
        }
        v.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        divisions.save(v);
        log.warn("Bus division {} deleted", v.getId());
        auditService.record(AuditAction.BUS_DIVISION_DELETED, ENTITY, v.getId(), Map.of("divisionCode", v.getDivisionCode()));
    }

    private DivisionView view(BusDivision v, UUID companyId) {
        List<BusDepot> mine = depots.search(companyId, null, null, null, v.getId(), null);
        long depotsN = mine.stream().filter(d -> d.getDepotType() == BusDepotType.BUS_DEPOT).count();
        return new DivisionView(v, depotsN, mine.size() - depotsN);
    }

    private void requireUnique(UUID companyId, BusDivision v, UUID excludeId) {
        if (divisions.existsByCode(companyId, v.getDivisionCode(), excludeId)) {
            throw new DuplicateResourceException(ENTITY, "divisionCode", v.getDivisionCode());
        }
        if (divisions.existsByName(companyId, v.getDivisionName(), excludeId)) {
            throw new DuplicateResourceException(ENTITY, "divisionName", v.getDivisionName());
        }
    }

    private BusDivision load(UUID id, UUID companyId) {
        return divisions.findByIdWithinCompany(id, companyId).orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Bus divisions belong to a company."));
    }
}
