package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeCommand;
import com.courier.modules.charge.application.command.UpdateChargeCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeCriteria;
import com.courier.modules.charge.domain.ChargeRepository;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeSpecifications;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.master.application.ServiceTypeService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Charge use cases. Per-method {@code @PreAuthorize} — see {@link ChargeService} for the
 * audience: {@code COMPANY_ADMIN} for everything, both reads and writes. Company
 * isolation is the project's two layers: the Hibernate filter, plus
 * {@code findByIdWithinCompany} on every single-row load, so a foreign charge id is a 404.
 *
 * <p>{@code serviceTypeId} is validated against {@code com.courier.modules.master}'s own
 * application service interface — a forward cross-feature dependency, not a port, the
 * same treatment {@code RateServiceImpl} gives the master lists it prices against.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private static final String ENTITY = "Charge";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final ChargeRepository repository;
    private final ChargeSettingRepository settingRepository;
    private final ServiceTypeService serviceTypeService;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public Charge create(CreateChargeCommand command) {
        UUID companyId = requireCompany();
        requireServiceTypeExists(command.serviceTypeId());

        Charge charge = Charge.builder()
                .chargeName(command.chargeName())
                .serviceTypeId(command.serviceTypeId())
                .status(ChargeStatus.ACTIVE)
                .build();
        charge.applyInvariants();
        requireNameAvailable(companyId, charge.getServiceTypeId(), charge.getChargeName(), null);

        Charge saved = repository.save(charge);
        log.info("Charge {} ({}) created in company {} by {}",
                saved.getChargeName(), saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.CHARGE_CREATED, ENTITY, saved.getId(),
                Map.of("chargeName", saved.getChargeName(), "serviceTypeId", String.valueOf(saved.getServiceTypeId())));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public Charge update(UUID id, UpdateChargeCommand command) {
        UUID companyId = requireCompany();
        Charge charge = loadOrThrow(id, companyId);
        requireCurrentVersion(charge, command.expectedVersion());
        requireServiceTypeExists(command.serviceTypeId());

        Map<String, Object> before = snapshot(charge);

        charge.setChargeName(command.chargeName());
        charge.setServiceTypeId(command.serviceTypeId());
        charge.applyInvariants();
        requireNameAvailable(companyId, charge.getServiceTypeId(), charge.getChargeName(), charge.getId());

        Charge saved = repository.save(charge);
        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("Charge {} updated in company {} by {} ({} field(s))",
                saved.getChargeName(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.CHARGE_UPDATED, ENTITY, saved.getId(), changes);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public Charge getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public Page<Charge> search(ChargeCriteria criteria, Pageable pageable) {
        ChargeCriteria safe = criteria == null ? ChargeCriteria.none() : criteria;
        return repository.findAll(ChargeSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public Charge activate(UUID id) {
        UUID companyId = requireCompany();
        Charge charge = loadOrThrow(id, companyId);
        if (charge.isActive()) {
            return charge;
        }
        charge.activate();
        Charge saved = repository.save(charge);
        auditService.record(AuditAction.CHARGE_ACTIVATED, ENTITY, saved.getId(),
                Map.of("chargeName", saved.getChargeName()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public Charge deactivate(UUID id) {
        UUID companyId = requireCompany();
        Charge charge = loadOrThrow(id, companyId);
        if (!charge.isActive()) {
            return charge;
        }
        charge.deactivate();
        Charge saved = repository.save(charge);
        auditService.record(AuditAction.CHARGE_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("chargeName", saved.getChargeName()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        Charge charge = loadOrThrow(id, companyId);

        long liveSettings = settingRepository.countByCompanyIdAndChargeId(companyId, charge.getId());
        if (liveSettings > 0) {
            throw new BusinessRuleException(
                    "Charge \"%s\" still has %d charge setting(s). Remove them first."
                            .formatted(charge.getChargeName(), liveSettings));
        }

        charge.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.saveAndFlush(charge);
        log.warn("Charge {} ({}) soft deleted in company {} by {}",
                charge.getChargeName(), charge.getId(), companyId, currentActor());
        auditService.record(AuditAction.CHARGE_DELETED, ENTITY, charge.getId(),
                Map.of("chargeName", charge.getChargeName()));
    }

    // -------------------------------------------------------------------- helpers

    Charge loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireNameAvailable(UUID companyId, UUID serviceTypeId, String chargeName, UUID excludeId) {
        if (repository.isNameTaken(companyId, serviceTypeId, chargeName, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "chargeName", chargeName);
        }
    }

    private void requireServiceTypeExists(UUID serviceTypeId) {
        if (serviceTypeId == null) {
            throw new BusinessRuleException("A service type is required.");
        }
        try {
            serviceTypeService.getById(serviceTypeId);
        } catch (ResourceNotFoundException e) {
            throw new BusinessRuleException("No such service type: " + serviceTypeId);
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Charges belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(Charge charge, Long expected) {
        if (expected == null) {
            return;
        }
        if (!Objects.equals(charge.getVersion(), expected)) {
            throw new ObjectOptimisticLockingFailureException(Charge.class, charge.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }

    private Map<String, Object> snapshot(Charge c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("chargeName", c.getChargeName());
        v.put("serviceTypeId", String.valueOf(c.getServiceTypeId()));
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
