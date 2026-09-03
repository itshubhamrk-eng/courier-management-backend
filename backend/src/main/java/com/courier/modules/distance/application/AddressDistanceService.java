package com.courier.modules.distance.application;

import com.courier.modules.company.application.geocoding.BranchGeocoder;
import com.courier.modules.company.application.geocoding.GeocodingPort;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.modules.customer.domain.CustomerAddressRepository;
import com.courier.modules.distance.application.routing.RoutingPort;
import com.courier.modules.distance.domain.AddressDistance;
import com.courier.modules.distance.domain.AddressDistanceRepository;
import com.courier.modules.distance.domain.AddressType;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Resolves and caches the road distance between two addresses of the same {@link
 * AddressType}. {@code BRANCH} reads {@code Branch}'s own latitude/longitude — geocoding
 * it on demand via {@link BranchGeocoder}, the same helper {@code BranchServiceImpl} uses
 * on create/update, if it turns out to be missing, and persisting the result so it never
 * has to be looked up again. {@code CUSTOMER} reads {@code CustomerAddress} (the address
 * carries the coordinates, a customer itself does not — {@code fromId}/{@code toId} for
 * {@code CUSTOMER} are {@code customer_addresses.id}, not {@code customers.id}); nothing
 * geocodes a customer address on demand yet, matching {@code CustomerAddressServiceImpl}
 * not doing it on save either.
 *
 * <p>Once resolved, a pair is cached in {@code address_distance} and not recomputed on
 * every request — {@link #refresh} is the explicit escape hatch for when the underlying
 * address's location changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressDistanceService {

    private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1000);
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
    private static final String ENTITY = "AddressDistance";

    private final AddressDistanceRepository repository;
    private final BranchRepository branchRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final RoutingPort routingPort;
    private final GeocodingPort geocodingPort;

    // ------------------------------------------------------------------- resolve

    /**
     * {@code REQUIRES_NEW}, not the default {@code REQUIRED}: {@link
     * com.courier.modules.freight.application.FreightFactorServiceImpl#tryCalculate}
     * calls this expecting to catch an unresolved-geocode {@link BusinessRuleException}
     * and keep going in its own (usually the booking request's) transaction — but under
     * the default propagation, this method's transactional proxy marks the shared
     * physical transaction rollback-only the instant it throws, regardless of whether the
     * caller catches it afterward. The caller's later commit then fails outright with
     * Spring's own {@code UnexpectedRollbackException}, surfacing as a generic 500 to a
     * booking clerk who just typed a pincode — confirmed live: {@code
     * PricingEngineImpl.calculate -> FreightFactorServiceImpl.tryCalculate ->
     * resolveBranchDistance} was exactly this chain. Running in its own transaction lets
     * a failure here be caught and shrugged off without poisoning whatever transaction
     * called in.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @PreAuthorize("isAuthenticated()")
    public AddressDistance resolveBranchDistance(UUID fromBranchId, UUID toBranchId) {
        return resolve(AddressType.BRANCH, fromBranchId, toBranchId);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public AddressDistance resolveCustomerAddressDistance(UUID fromAddressId, UUID toAddressId) {
        return resolve(AddressType.CUSTOMER, fromAddressId, toAddressId);
    }

    private AddressDistance resolve(AddressType type, UUID fromId, UUID toId) {
        requireDistinctPair(fromId, toId);
        UUID companyId = requireCompany();
        return repository.findByAddressTypeAndFromIdAndToId(type, fromId, toId)
                .orElseGet(() -> compute(companyId, type, fromId, toId));
    }

    // --------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public AddressDistance get(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    /** Any combination of filters; all optional. */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<AddressDistance> search(AddressType addressType, UUID fromId, UUID toId) {
        return repository.search(requireCompany(), addressType, fromId, toId);
    }

    // ------------------------------------------------------------------- refresh

    /** Recomputes a resolved pair in place — the escape hatch once an address moves. */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public AddressDistance refresh(UUID id) {
        UUID companyId = requireCompany();
        AddressDistance existing = loadOrThrow(id, companyId);

        Located from = locate(existing.getAddressType(), existing.getFromId(), companyId);
        Located to = locate(existing.getAddressType(), existing.getToId(), companyId);
        requireLocated(from, to);

        RoutingPort.RouteResult route = route(from, to);
        applyRoute(existing, route);
        AddressDistance saved = repository.save(existing);
        log.info("Address distance {} refreshed: {} -> {} ({} km, {} min)",
                saved.getAddressType(), saved.getFromId(), saved.getToId(),
                saved.getDistanceKm(), saved.getRequiredTimeMinutes());
        return saved;
    }

    // ------------------------------------------------------------------- delete

    /** Soft delete. The pair can be resolved again afterwards, computing a fresh row. */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        AddressDistance existing = loadOrThrow(id, requireCompany());
        existing.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(existing);
        log.info("Address distance {} ({} -> {}) deleted", existing.getAddressType(),
                existing.getFromId(), existing.getToId());
    }

    // ---------------------------------------------------------------- computation

    private AddressDistance compute(UUID companyId, AddressType type, UUID fromId, UUID toId) {
        Located from = locate(type, fromId, companyId);
        Located to = locate(type, toId, companyId);
        requireLocated(from, to);

        RoutingPort.RouteResult route = route(from, to);

        AddressDistance distance = AddressDistance.builder()
                .addressType(type)
                .fromId(fromId)
                .toId(toId)
                .build();
        applyRoute(distance, route);

        AddressDistance saved = insertOrRestore(companyId, type, fromId, toId, distance);
        log.info("Address distance resolved: {} {} -> {} ({} km, {} min)",
                type, fromId, toId, saved.getDistanceKm(), saved.getRequiredTimeMinutes());
        return saved;
    }

    /**
     * A pair deleted and then re-resolved must compute a fresh row (the service's own
     * contract on {@link #delete}) — but {@code uk_address_distance_pair} isn't scoped by
     * {@code deleted}, so a plain insert into an already-occupied slot fails at flush time,
     * too late in the transaction to recover from cleanly. Checked up front instead: if a
     * soft-deleted row already holds this pair, un-delete and refresh it; only insert when
     * the slot is genuinely free.
     */
    private AddressDistance insertOrRestore(UUID companyId, AddressType type, UUID fromId, UUID toId,
                                            AddressDistance candidate) {
        byte[] companyIdBytes = TimeOrderedUuid.toBytes(companyId);
        byte[] fromIdBytes = TimeOrderedUuid.toBytes(fromId);
        byte[] toIdBytes = TimeOrderedUuid.toBytes(toId);

        if (repository.countDeletedPair(companyIdBytes, type.name(), fromIdBytes, toIdBytes) > 0) {
            repository.restoreAndUpdate(companyIdBytes, type.name(), fromIdBytes, toIdBytes,
                    candidate.getDistanceKm(), candidate.getDistanceMeter(), candidate.getRequiredTimeMinutes());
            return repository.findByAddressTypeAndFromIdAndToId(type, fromId, toId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Address distance restored but not found immediately after"));
        }
        return repository.save(candidate);
    }

    private RoutingPort.RouteResult route(Located from, Located to) {
        return routingPort.route(
                        new RoutingPort.Coordinates(from.latitude(), from.longitude()),
                        new RoutingPort.Coordinates(to.latitude(), to.longitude()))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                        "The distance between these addresses could not be resolved. Please retry."));
    }

    private void applyRoute(AddressDistance distance, RoutingPort.RouteResult route) {
        BigDecimal distanceMeter = route.distanceMeters().setScale(2, RoundingMode.HALF_UP);
        distance.setDistanceMeter(distanceMeter);
        distance.setDistanceKm(distanceMeter.divide(METERS_PER_KM, 3, RoundingMode.HALF_UP));
        distance.setRequiredTimeMinutes(
                route.durationSeconds().divide(SECONDS_PER_MINUTE, 2, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------- location

    private record Located(BigDecimal latitude, BigDecimal longitude) {
        boolean isKnown() {
            return latitude != null && longitude != null;
        }
    }

    private Located locate(AddressType type, UUID id, UUID companyId) {
        return switch (type) {
            case BRANCH -> {
                Branch branch = branchRepository.findByIdWithinCompany(id, companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
                // On-demand fallback: a branch created before geocode-on-create existed
                // (or one whose geocode simply never ran) shouldn't need a separate visit
                // to Branches first just to unblock resolving its distance.
                if (BranchGeocoder.fillIfMissing(geocodingPort, branch)) {
                    branchRepository.save(branch);
                }
                yield new Located(branch.getLatitude(), branch.getLongitude());
            }
            case CUSTOMER -> {
                CustomerAddress address = customerAddressRepository.findByIdWithinCompany(id, companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer address", id));
                yield new Located(address.getLatitude(), address.getLongitude());
            }
        };
    }

    private void requireLocated(Located from, Located to) {
        if (!from.isKnown() || !to.isKnown()) {
            throw new BusinessRuleException(
                    "Both addresses need a resolved location before their distance can be "
                            + "calculated. Set latitude/longitude directly, or re-save the "
                            + "address so it can be geocoded.");
        }
    }

    private void requireDistinctPair(UUID fromId, UUID toId) {
        if (fromId == null || toId == null) {
            throw new BusinessRuleException("Both addresses are required.");
        }
        if (fromId.equals(toId)) {
            throw new BusinessRuleException("An address's distance to itself is not meaningful.");
        }
    }

    private AddressDistance loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
    }
}
