package com.courier.modules.customerpricing.application;

import com.courier.modules.customer.application.event.CustomerEvent;
import com.courier.modules.customer.domain.CustomerType;
import com.courier.modules.customerpricing.domain.FallbackScope;
import com.courier.modules.customerpricing.domain.PricingZone;
import com.courier.modules.customerpricing.domain.PricingZoneRepository;
import com.courier.modules.customerpricing.domain.ZoneStatus;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Gives every new business customer its two catch-all pricing zones, "Rest of Maharashtra" and
 * "Out of Maharashtra", so the rate cards are there to fill in from day one.
 *
 * <p>Same shape as the wallet provisioning for branches: AFTER_COMMIT + REQUIRES_NEW so a zone
 * failure can never roll the customer back, and it writes through the repository rather than
 * {@code PricingZoneService} because customer creation is open to roles that may not create
 * pricing zones. The zones start without a rate, and {@code resolve} skips a catch-all zone that
 * has no active rate, so an unpriced zone changes nothing about how bookings price.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatchAllZoneProvisioningListener {

    private static final Map<FallbackScope, String> NAMES = Map.of(
            FallbackScope.REST_OF_STATE, "Rest of Maharashtra",
            FallbackScope.OUT_OF_STATE, "Out of Maharashtra");

    private final PricingZoneRepository zones;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(CustomerEvent.CustomerCreated event) {
        if (event.customerType() != CustomerType.BUSINESS) {
            return;
        }
        try {
            CompanyContext.runAs(event.companyId(), () -> provision(event.companyId(), event.customerId()));
        } catch (RuntimeException e) {
            // Never fail the request that created the customer; the zones can be added by hand.
            log.error("Could not create the catch-all zones for customer {}; add them under Pricing Zone",
                    event.customerId(), e);
        }
    }

    private void provision(UUID companyId, UUID customerId) {
        for (FallbackScope scope : FallbackScope.values()) {
            if (zones.existsFallback(companyId, customerId, scope, null)) {
                continue;
            }
            PricingZone zone = PricingZone.builder()
                    .customerId(customerId).zoneName(NAMES.get(scope))
                    .fallbackScope(scope).status(ZoneStatus.ACTIVE).build();
            zone.applyInvariants();
            zones.save(zone);
        }
    }
}
