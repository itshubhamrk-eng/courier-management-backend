package com.courier.modules.freight.application;

import com.courier.modules.freight.application.command.CreateFreightFactorCommand;
import com.courier.modules.freight.application.command.FreightCalculationCommand;
import com.courier.modules.freight.application.command.UpdateFreightFactorCommand;
import com.courier.modules.freight.domain.FreightFactor;
import com.courier.modules.freight.domain.FreightFactorCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Use cases for the freight factor grid — a standalone, company-level pricing mechanism
 * independent of Rate Master/Pricing Engine.
 *
 * <p><b>Audiences:</b>
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — creates, updates, activates and deactivates cells.
 *       Pricing is a company-level decision, the same tier {@code RateServiceImpl}
 *       writes at.</li>
 *   <li>Any authenticated company user — reads and calculates, same reasoning
 *       {@code RateService#calculate} documents.</li>
 * </ul>
 * There is no delete: a cell is withdrawn by deactivating it, never removed, same
 * convention as {@code Rate}.
 */
public interface FreightFactorService {

    FreightFactor create(CreateFreightFactorCommand command);

    FreightFactor update(UUID id, UpdateFreightFactorCommand command);

    FreightFactor getById(UUID id);

    Page<FreightFactor> search(FreightFactorCriteria criteria, Pageable pageable);

    FreightFactor activate(UUID id);

    FreightFactor deactivate(UUID id);

    /**
     * Resolves the distance between the two branches (via {@code AddressDistanceService},
     * cache-or-resolve), matches the ACTIVE grid cell whose distance range and weight
     * range both cover the request, and returns {@code freight = factor * weight}.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the branches' distance
     *         cannot be resolved, or no cell covers this distance/weight combination
     */
    FreightCalculationResult calculate(FreightCalculationCommand command);
}
