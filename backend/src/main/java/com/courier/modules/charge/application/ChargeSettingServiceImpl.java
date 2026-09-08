package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeSettingCommand;
import com.courier.modules.charge.application.command.UpdateChargeSettingCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeSetting;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
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

/**
 * Charge Setting use cases. Per-method {@code @PreAuthorize}, {@code COMPANY_ADMIN} for
 * everything — see {@link ChargeSettingService}. {@code chargeId} is validated against
 * {@link ChargeService} (a same-module, cross-bean call, not self-invocation) rather than
 * a physical FK error, so a spoofed or foreign charge id is refused with a clear message
 * instead of a database constraint violation.
 *
 * <p>No two ACTIVE slab settings under the same charge, of the same
 * {@code chargeSlabType}, may cover the same KG/KM band — MySQL has no exclusion
 * constraint, so this runs here, on create, on update (while the setting stays active),
 * and on activation, the same "deactivate, add an overlapping slab, reactivate" loophole
 * {@code RateServiceImpl.requireNoOverlap} already closes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeSettingServiceImpl implements ChargeSettingService {

    private static final String ENTITY = "ChargeSetting";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final ChargeSettingRepository repository;
    private final ChargeService chargeService;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public ChargeSetting create(CreateChargeSettingCommand command) {
        UUID companyId = requireCompany();
        Charge charge = requireCharge(command.chargeId());

        ChargeSetting setting = ChargeSetting.builder()
                .chargeId(charge.getId())
                .chargeType(command.chargeType())
                .chargeSlabType(command.chargeSlabType())
                .fromKm(command.fromKm())
                .toKm(command.toKm())
                .fromKg(command.fromKg())
                .toKg(command.toKg())
                .chargeValue(command.chargeValue())
                .chargeValueType(command.chargeValueType())
                .commissionType(command.commissionType())
                .commissionValue(command.commissionValue())
                .status(ChargeStatus.ACTIVE)
                .build();
        setting.applyInvariants();
        requireNoOverlap(setting, companyId);

        ChargeSetting saved = repository.save(setting);
        log.info("Charge setting ({}) created under charge {} in company {} by {}",
                saved.getId(), charge.getId(), companyId, currentActor());
        auditService.record(AuditAction.CHARGE_SETTING_CREATED, ENTITY, saved.getId(),
                Map.of("chargeId", String.valueOf(saved.getChargeId())));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public ChargeSetting update(UUID id, UpdateChargeSettingCommand command) {
        UUID companyId = requireCompany();
        ChargeSetting setting = loadOrThrow(id, companyId);
        requireCurrentVersion(setting, command.expectedVersion());

        setting.setChargeType(command.chargeType());
        setting.setChargeSlabType(command.chargeSlabType());
        setting.setFromKm(command.fromKm());
        setting.setToKm(command.toKm());
        setting.setFromKg(command.fromKg());
        setting.setToKg(command.toKg());
        setting.setChargeValue(command.chargeValue());
        setting.setChargeValueType(command.chargeValueType());
        setting.setCommissionType(command.commissionType());
        setting.setCommissionValue(command.commissionValue());
        setting.applyInvariants();
        if (setting.isActive()) {
            requireNoOverlap(setting, companyId);
        }

        ChargeSetting saved = repository.save(setting);
        log.info("Charge setting {} updated in company {} by {}", saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.CHARGE_SETTING_UPDATED, ENTITY, saved.getId(),
                Map.of("chargeId", String.valueOf(saved.getChargeId())));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public ChargeSetting getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public List<ChargeSetting> listByCharge(UUID chargeId) {
        UUID companyId = requireCompany();
        requireCharge(chargeId);
        return repository.findByCompanyIdAndChargeIdOrderByCreatedAtAsc(companyId, chargeId);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public ChargeSetting activate(UUID id) {
        UUID companyId = requireCompany();
        ChargeSetting setting = loadOrThrow(id, companyId);
        if (setting.isActive()) {
            return setting;
        }
        setting.activate();
        requireNoOverlap(setting, companyId);
        ChargeSetting saved = repository.save(setting);
        auditService.record(AuditAction.CHARGE_SETTING_ACTIVATED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public ChargeSetting deactivate(UUID id) {
        UUID companyId = requireCompany();
        ChargeSetting setting = loadOrThrow(id, companyId);
        if (!setting.isActive()) {
            return setting;
        }
        setting.deactivate();
        ChargeSetting saved = repository.save(setting);
        auditService.record(AuditAction.CHARGE_SETTING_DEACTIVATED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        ChargeSetting setting = loadOrThrow(id, companyId);
        setting.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.saveAndFlush(setting);
        log.warn("Charge setting {} soft deleted in company {} by {}", setting.getId(), companyId, currentActor());
        auditService.record(AuditAction.CHARGE_SETTING_DELETED, ENTITY, setting.getId(), Map.of());
    }

    // -------------------------------------------------------------------- helpers

    ChargeSetting loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private Charge requireCharge(UUID chargeId) {
        if (chargeId == null) {
            throw new BusinessRuleException("A charge setting must belong to a charge.");
        }
        try {
            return chargeService.getById(chargeId);
        } catch (ResourceNotFoundException e) {
            throw new BusinessRuleException("No such charge: " + chargeId);
        }
    }

    /**
     * No two ACTIVE slab settings under the same charge, of the same slab type, may cover
     * the same band. Only {@code SLAB} settings are compared — {@code FACTOR} settings
     * carry no band to overlap.
     */
    private void requireNoOverlap(ChargeSetting candidate, UUID companyId) {
        if (candidate.getChargeType() != ChargeType.SLAB) {
            return;
        }
        List<ChargeSetting> existing = repository.findByCompanyIdAndChargeIdAndStatus(
                companyId, candidate.getChargeId(), ChargeStatus.ACTIVE);

        for (ChargeSetting other : existing) {
            if (Objects.equals(other.getId(), candidate.getId())) {
                continue;
            }
            if (candidate.overlaps(other)) {
                throw new BusinessRuleException(
                        ("This %s slab overlaps another active setting (%s) under the same charge. "
                                + "Slabs are [from, to), so they may touch but not overlap.")
                                .formatted(candidate.getChargeSlabType(), other.getId()));
            }
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Charge settings belong to a company, "
                        + "so this operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(ChargeSetting setting, Long expected) {
        if (expected == null) {
            return;
        }
        if (!Objects.equals(setting.getVersion(), expected)) {
            throw new ObjectOptimisticLockingFailureException(ChargeSetting.class, setting.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }
}
