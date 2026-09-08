package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeSettingCommand;
import com.courier.modules.charge.application.command.UpdateChargeSettingCommand;
import com.courier.modules.charge.domain.ChargeSetting;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for Charge Settings — the rows under one {@code Charge}. {@code COMPANY_ADMIN}
 * only, both reads and writes, same audience as {@link ChargeService}.
 */
public interface ChargeSettingService {

    ChargeSetting create(CreateChargeSettingCommand command);

    ChargeSetting update(UUID id, UpdateChargeSettingCommand command);

    ChargeSetting getById(UUID id);

    /** Every setting under one charge, in any status — the parent's own detail view. */
    List<ChargeSetting> listByCharge(UUID chargeId);

    ChargeSetting activate(UUID id);

    ChargeSetting deactivate(UUID id);

    /** Soft delete. A setting has no children of its own, so nothing else guards this. */
    void delete(UUID id);
}
