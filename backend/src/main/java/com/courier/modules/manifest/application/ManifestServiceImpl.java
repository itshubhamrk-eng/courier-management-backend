package com.courier.modules.manifest.application;

import com.courier.modules.company.application.UserService;
import com.courier.modules.manifest.application.command.CreateManifestCommand;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.manifest.domain.ManifestCriteria;
import com.courier.modules.manifest.domain.ManifestNumberGenerator;
import com.courier.modules.manifest.domain.ManifestRepository;
import com.courier.modules.manifest.domain.ManifestShipmentAggregate;
import com.courier.modules.manifest.domain.ManifestSpecifications;
import com.courier.modules.manifest.domain.ManifestSummaryStats;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates both directions Shipment Movement needs from a manifest: creating one
 * (attaching shipments, each of which only {@code modules.shipment} can validate and
 * mutate) and dispatching one (assigning the vehicle/driver, then moving the manifest's
 * own {@code MANIFEST_CREATED} shipments to {@code DISPATCHED}). Manifest is the only module that
 * calls the other — see {@code ShipmentService}'s own "Shipment Movement" section header
 * for why that one-directional arrow was chosen over the reverse: a two-way Spring bean
 * dependency between the two modules' services would be a circular-dependency startup
 * failure, not just an architectural smell.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManifestServiceImpl implements ManifestService {

    private static final String ENTITY = "Manifest";
    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.OPERATOR + "')";
    private static final String READERS = "isAuthenticated()";

    private static final int NUMBER_ATTEMPTS = 5;

    private final ManifestRepository manifestRepository;
    private final ShipmentService shipmentService;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Manifest create(CreateManifestCommand command) {
        UUID companyId = requireCompany();

        if (command.bookingBranchId() == null || command.deliveryBranchId() == null) {
            throw new BusinessRuleException("A manifest needs both a booking branch and a delivery branch.");
        }
        if (command.shipmentIds() == null || command.shipmentIds().isEmpty()) {
            throw new BusinessRuleException("A manifest needs at least one shipment.");
        }

        Manifest manifest = Manifest.builder()
                .manifestNumber(nextManifestNumber(companyId))
                .bookingBranchId(command.bookingBranchId())
                .deliveryBranchId(command.deliveryBranchId())
                .remarks(command.remarks())
                .build();
        Manifest saved = manifestRepository.save(manifest);

        for (UUID shipmentId : command.shipmentIds()) {
            shipmentService.attachToManifest(shipmentId, saved.getId(),
                    command.bookingBranchId(), command.deliveryBranchId());
        }

        log.info("Manifest {} ({}) created in company {} with {} shipment(s) by {}",
                saved.getManifestNumber(), saved.getId(), companyId, command.shipmentIds().size(),
                currentActor());
        auditService.record(AuditAction.MANIFEST_CREATED, ENTITY, saved.getId(),
                Map.of("manifestNumber", saved.getManifestNumber(),
                        "shipmentCount", command.shipmentIds().size()));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Manifest getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<Manifest> search(ManifestCriteria criteria, Pageable pageable) {
        return manifestRepository.findAll(ManifestSpecifications.matching(criteria), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public ManifestSummaryStats summaryStats(ManifestCriteria criteria) {
        List<Manifest> matches = manifestRepository.findAll(ManifestSpecifications.matching(criteria));
        List<UUID> ids = matches.stream().map(Manifest::getId).toList();
        ManifestShipmentAggregate aggregate = ManifestShipmentAggregate.of(shipmentService.findByManifestIds(ids));
        return new ManifestSummaryStats(matches.size(), aggregate.shipmentCount(),
                aggregate.totalWeight(), aggregate.totalPackages());
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Manifest dispatch(UUID id, UUID vehicleId, UUID driverUserId, Instant departureTime) {
        UUID companyId = requireCompany();
        Manifest manifest = loadOrThrow(id, companyId);

        if (manifest.isDispatched()) {
            throw new BusinessRuleException(
                    "Manifest %s has already been dispatched.".formatted(manifest.getManifestNumber()));
        }

        List<Shipment> readyShipments = shipmentService.findManifestCreatedShipments(manifest.getId());
        if (readyShipments.isEmpty()) {
            throw new BusinessRuleException(
                    "Manifest %s has no shipment to dispatch.".formatted(manifest.getManifestNumber()));
        }

        Vehicle vehicle = vehicleService.getById(vehicleId);
        if (!vehicle.isActive()) {
            throw new BusinessRuleException(
                    "Vehicle %s is not active.".formatted(vehicle.getVehicleNumber()));
        }
        // A driver is any real user of this company — see the module doc for why no
        // company role currently models "driver" cleanly enough to restrict further.
        userService.getById(driverUserId);

        manifest.dispatch(vehicleId, driverUserId, departureTime);
        Manifest saved = manifestRepository.save(manifest);

        shipmentService.transitionToDispatched(
                readyShipments.stream().map(Shipment::getId).toList(),
                saved.getId(), vehicleId, saved.getBookingBranchId());

        log.info("Manifest {} ({}) dispatched in company {} with vehicle {} by {}",
                saved.getManifestNumber(), saved.getId(), companyId, vehicleId, currentActor());

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public void removeShipment(UUID manifestId, UUID shipmentId) {
        UUID companyId = requireCompany();
        Manifest manifest = loadOrThrow(manifestId, companyId);

        if (manifest.isDispatched()) {
            throw new BusinessRuleException(
                    "Manifest %s has already been dispatched.".formatted(manifest.getManifestNumber()));
        }

        shipmentService.detachFromManifest(shipmentId, manifestId);

        log.info("Shipment {} removed from manifest {} ({}) in company {} by {}",
                shipmentId, manifest.getManifestNumber(), manifestId, companyId, currentActor());
        auditService.record(AuditAction.MANIFEST_SHIPMENT_REMOVED, ENTITY, manifestId,
                Map.of("shipmentId", shipmentId.toString()));
    }

    private Manifest loadOrThrow(UUID id, UUID companyId) {
        return manifestRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private String nextManifestNumber(UUID companyId) {
        for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
            String candidate = ManifestNumberGenerator.generate();
            if (!manifestRepository.existsByCompanyIdAndManifestNumber(companyId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique manifest number in " + NUMBER_ATTEMPTS + " attempts");
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Manifests belong to a company."));
    }

    private String currentActor() {
        return com.courier.shared.security.SecurityUtils.getCurrentUser()
                .map(u -> u.email() == null ? u.userId().toString() : u.email())
                .orElse("system");
    }
}
