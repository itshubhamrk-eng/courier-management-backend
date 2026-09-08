package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeCommand;
import com.courier.modules.charge.application.command.UpdateChargeCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Use cases for Charges. {@code COMPANY_ADMIN} only, both reads and writes — this is a
 * company's own pricing configuration, not something a booking desk consults yet (that
 * integration is a separate, later piece of work).
 */
public interface ChargeService {

    Charge create(CreateChargeCommand command);

    Charge update(UUID id, UpdateChargeCommand command);

    Charge getById(UUID id);

    Page<Charge> search(ChargeCriteria criteria, Pageable pageable);

    Charge activate(UUID id);

    Charge deactivate(UUID id);

    /**
     * Soft delete. Refused while the charge still has any live (non-deleted) Charge
     * Setting — the same "a parent with live children cannot be deleted" rule Master
     * Data's geography hierarchy already enforces, so a setting never ends up pointing at
     * a charge that no longer exists.
     */
    void delete(UUID id);
}
