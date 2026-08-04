package com.courier.modules.rate.application;

import com.courier.modules.rate.application.command.CreateRateCommand;
import com.courier.modules.rate.application.command.RateCalculationCommand;
import com.courier.modules.rate.application.command.UpdateRateCommand;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.rate.domain.RateCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Use cases for rate cards.
 *
 * <p><b>Audiences:</b>
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — creates, updates, activates and deactivates rates. Pricing
 *       is a company-level decision, the same tier {@code RouteServiceImpl} and
 *       {@code WeightSlabServiceImpl} write at.</li>
 *   <li>Any authenticated company user — reads and calculates. A booking clerk needs a
 *       quote to book a shipment; withholding {@link #calculate} from them would only push
 *       the booking screen into hard-coding prices.</li>
 * </ul>
 * There is no delete: {@code RATE_MASTER_DELETE} stays a seeded-but-unused permission code,
 * the same pattern {@code CUSTOMER_DELETE} and {@code MASTER_DATA_IMPORT} already follow —
 * a rate is withdrawn by deactivating it, never removed, because shipments already booked
 * against it still have to be explainable.
 */
public interface RateService {

    Rate create(CreateRateCommand command);

    Rate update(UUID id, UpdateRateCommand command);

    Rate getById(UUID id);

    Page<Rate> search(RateCriteria criteria, Pageable pageable);

    Rate activate(UUID id);

    Rate deactivate(UUID id);

    /**
     * Prices a shipment without booking it. Resolves the route from the booking/delivery
     * branch pair, matches the ACTIVE rate whose Route + Service Type + Package Type +
     * Payment Mode + weight slab covers the request, and computes the full breakdown.
     *
     * @throws com.courier.shared.exception.BusinessRuleException no route runs between the
     *         two branches, its route is inactive, or no rate covers this combination and
     *         weight on the given booking date
     */
    RateCalculationResult calculate(RateCalculationCommand command);

    /**
     * Every ACTIVE rate for one Route + Service Type + Package Type + Payment Mode
     * combination whose effective window covers {@code bookingDate} — the candidate slabs
     * {@link #calculate} matches against. Same read audience as {@link #calculate}.
     *
     * <p>Added for the Pricing Engine (a separate, reusable module), which runs its own
     * weight-slab matching and charge calculators over these candidates rather than
     * {@link #calculate}'s fixed formula — the same "small addition to an already-shipped
     * module" seam {@link com.courier.modules.master.application.RouteService#findByBranches}
     * was added by.
     */
    List<Rate> findActiveCandidates(UUID routeId, UUID serviceTypeId, UUID packageTypeId,
                                    UUID paymentModeId, LocalDate bookingDate);
}
