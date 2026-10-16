package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusCrossingRoute;
import com.courier.modules.busdepot.domain.BusCrossingRouteRepository;
import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotRepository;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.ShipmentBusCrossing;
import com.courier.modules.busdepot.domain.ShipmentBusCrossingRepository;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusCrossingServiceImpl implements BusCrossingService {

    private static final String ENTITY = "BusCrossingRoute";
    /** The booking desk saves routes while booking, so shipment creators write too. */
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "') or hasAuthority('SHIPMENT_CREATE')";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final BusCrossingRouteRepository routes;
    private final ShipmentBusCrossingRepository shipmentCrossings;
    private final BusDepotRepository depots;
    private final ShipmentService shipmentService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<RouteView> search(BusDepotStatus status, UUID depotId) {
        UUID companyId = requireCompany();
        return views(routes.search(companyId, status, depotId), companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public RouteView getById(UUID id) {
        UUID companyId = requireCompany();
        return views(List.of(load(id, companyId)), companyId).get(0);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public RouteView save(UUID fromDepotId, UUID toDepotId, List<UUID> stops) {
        UUID companyId = requireCompany();
        validate(companyId, fromDepotId, toDepotId, stops);
        BusCrossingRoute route = upsert(companyId, fromDepotId, toDepotId, stops);
        auditService.record(AuditAction.BUS_CROSSING_ROUTE_SAVED, ENTITY, route.getId(),
                Map.of("from", fromDepotId.toString(), "to", toDepotId.toString(), "stops", stops.size()));
        return views(List.of(route), companyId).get(0);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public RouteView update(UUID id, List<UUID> stops) {
        UUID companyId = requireCompany();
        BusCrossingRoute route = load(id, companyId);
        validate(companyId, route.getFromDepotId(), route.getToDepotId(), stops);
        boolean duplicate = routes.findByPair(companyId, route.getFromDepotId(), route.getToDepotId()).stream()
                .anyMatch(r -> !r.getId().equals(id) && r.getStops().equals(stops));
        if (duplicate) {
            throw new BusinessRuleException("Another saved crossing for this trip already has exactly these points.");
        }
        route.replaceStops(stops);
        BusCrossingRoute saved = routes.save(route);
        auditService.record(AuditAction.BUS_CROSSING_ROUTE_SAVED, ENTITY, saved.getId(), Map.of("stops", stops.size()));
        return views(List.of(saved), companyId).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<RouteView> lookup(UUID fromDepotId, UUID toDepotId, UUID fromBranchId, UUID toBranchId) {
        UUID companyId = requireCompany();
        Set<UUID> froms = candidates(companyId, fromDepotId, fromBranchId);
        Set<UUID> tos = candidates(companyId, toDepotId, toBranchId);
        if (froms.isEmpty() || tos.isEmpty()) {
            return List.of();
        }
        return views(routes.findActiveBetween(companyId, froms, tos), companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public RouteView activate(UUID id) {
        UUID companyId = requireCompany();
        BusCrossingRoute r = load(id, companyId);
        r.setStatus(BusDepotStatus.ACTIVE);
        BusCrossingRoute saved = routes.save(r);
        auditService.record(AuditAction.BUS_CROSSING_ROUTE_ACTIVATED, ENTITY, saved.getId(), Map.of());
        return views(List.of(saved), companyId).get(0);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public RouteView deactivate(UUID id) {
        UUID companyId = requireCompany();
        BusCrossingRoute r = load(id, companyId);
        r.setStatus(BusDepotStatus.INACTIVE);
        BusCrossingRoute saved = routes.save(r);
        auditService.record(AuditAction.BUS_CROSSING_ROUTE_DEACTIVATED, ENTITY, saved.getId(), Map.of());
        return views(List.of(saved), companyId).get(0);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public void delete(UUID id) {
        BusCrossingRoute r = load(id, requireCompany());
        r.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        routes.save(r);
        auditService.record(AuditAction.BUS_CROSSING_ROUTE_DELETED, ENTITY, r.getId(), Map.of());
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public ShipmentCrossingView setForShipment(UUID shipmentId, UUID fromDepotId, UUID toDepotId, List<UUID> stops,
                                               boolean saveAsRoute) {
        UUID companyId = requireCompany();
        shipmentService.getById(shipmentId); // 404 for a foreign / unknown shipment
        validate(companyId, fromDepotId, toDepotId, stops);
        UUID routeId = saveAsRoute ? upsert(companyId, fromDepotId, toDepotId, stops).getId() : null;
        ShipmentBusCrossing c = shipmentCrossings.findByShipment(shipmentId, companyId)
                .orElseGet(() -> ShipmentBusCrossing.builder().shipmentId(shipmentId).build());
        c.setRouteId(routeId != null ? routeId : c.getRouteId());
        c.setFromDepotId(fromDepotId);
        c.setToDepotId(toDepotId);
        c.replaceStops(stops);
        ShipmentBusCrossing saved = shipmentCrossings.save(c);
        auditService.record(AuditAction.SHIPMENT_BUS_CROSSING_SET, "Shipment", shipmentId,
                Map.of("from", fromDepotId.toString(), "to", toDepotId.toString(), "stops", stops.size()));
        return shipmentView(saved, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Optional<ShipmentCrossingView> getForShipment(UUID shipmentId) {
        UUID companyId = requireCompany();
        return shipmentCrossings.findByShipment(shipmentId, companyId).map(c -> shipmentView(c, companyId));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void clearForShipment(UUID shipmentId) {
        UUID companyId = requireCompany();
        shipmentCrossings.findByShipment(shipmentId, companyId).ifPresent(c -> {
            c.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
            shipmentCrossings.save(c);
        });
    }

    // ------------------------------------------------------------------ helpers

    private BusCrossingRoute upsert(UUID companyId, UUID from, UUID to, List<UUID> stops) {
        BusCrossingRoute route = routes.findByPair(companyId, from, to).stream()
                .filter(r -> r.getStops().equals(stops)).findFirst()
                .orElseGet(() -> {
                    BusCrossingRoute r = BusCrossingRoute.builder().fromDepotId(from).toDepotId(to).build();
                    r.replaceStops(stops);
                    return r;
                });
        route.setStatus(BusDepotStatus.ACTIVE);
        return routes.save(route);
    }

    /** From and To differ; at least one stop; every stop distinct and different from both ends; all active depots of this company. */
    private void validate(UUID companyId, UUID from, UUID to, List<UUID> stops) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Choose where the bus starts and where it ends.");
        }
        if (from.equals(to)) {
            throw new BusinessRuleException("The start and the end of a crossing route must be different.");
        }
        if (stops == null || stops.isEmpty()) {
            throw new BusinessRuleException("Add at least one crossing point.");
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID s : stops) {
            if (s == null || s.equals(from) || s.equals(to) || !seen.add(s)) {
                throw new BusinessRuleException("A crossing point cannot repeat or be the start or end of the route.");
            }
        }
        Set<UUID> all = new HashSet<>(stops);
        all.add(from);
        all.add(to);
        Map<UUID, BusDepot> found = depots.findAllById(all).stream()
                .filter(d -> companyId.equals(d.getCompanyId()))
                .collect(Collectors.toMap(BusDepot::getId, Function.identity()));
        for (UUID id : all) {
            BusDepot d = found.get(id);
            if (d == null) {
                throw new BusinessRuleException("A chosen depot / station does not exist in your company.");
            }
            if (!d.isActive()) {
                throw new BusinessRuleException(d.getDepotName() + " is inactive and cannot be used in a crossing route.");
            }
        }
    }

    /** The explicit depot, else every depot assigned to the branch. */
    private Set<UUID> candidates(UUID companyId, UUID depotId, UUID branchId) {
        if (depotId != null) {
            return Set.of(depotId);
        }
        if (branchId == null) {
            return Set.of();
        }
        return depots.search(companyId, BusDepotStatus.ACTIVE, null, branchId, null, null).stream()
                .map(BusDepot::getId).collect(Collectors.toSet());
    }

    private List<RouteView> views(List<BusCrossingRoute> list, UUID companyId) {
        Set<UUID> ids = new HashSet<>();
        for (BusCrossingRoute r : list) {
            ids.add(r.getFromDepotId());
            ids.add(r.getToDepotId());
            ids.addAll(r.getStops());
        }
        Map<UUID, BusDepot> byId = depotMap(ids, companyId);
        return list.stream().map(r -> new RouteView(r.getId(), r.getStatus(), byId.get(r.getFromDepotId()),
                byId.get(r.getToDepotId()), r.getStops().stream().map(byId::get).toList(), r.getVersion())).toList();
    }

    private ShipmentCrossingView shipmentView(ShipmentBusCrossing c, UUID companyId) {
        Set<UUID> ids = new HashSet<>(c.getStops());
        ids.add(c.getFromDepotId());
        ids.add(c.getToDepotId());
        Map<UUID, BusDepot> byId = depotMap(ids, companyId);
        return new ShipmentCrossingView(c.getId(), c.getShipmentId(), c.getRouteId(), byId.get(c.getFromDepotId()),
                byId.get(c.getToDepotId()), c.getStops().stream().map(byId::get).toList());
    }

    private Map<UUID, BusDepot> depotMap(Set<UUID> ids, UUID companyId) {
        return depots.findAllById(ids).stream().filter(d -> companyId.equals(d.getCompanyId()))
                .collect(Collectors.toMap(BusDepot::getId, Function.identity()));
    }

    private BusCrossingRoute load(UUID id, UUID companyId) {
        return routes.findByIdWithinCompany(id, companyId).orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Crossing routes belong to a company."));
    }
}
