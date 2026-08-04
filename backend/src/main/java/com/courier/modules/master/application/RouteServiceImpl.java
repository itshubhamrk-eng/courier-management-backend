package com.courier.modules.master.application;

import com.courier.modules.master.application.command.RouteCommand;
import com.courier.modules.master.domain.BranchLookupPort;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.Route;
import com.courier.modules.master.domain.RouteRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Routes: booking branch -> delivery branch, with a distance and a transit promise.
 *
 * <p>Both endpoints are validated through {@link BranchLookupPort}, this module's own seam
 * onto {@code modules/company}. A branch id from another company simply does not resolve,
 * so it is refused as unknown rather than linked — the port takes the company explicitly
 * and the adapter never answers across companies.
 *
 * <p>A branch must be <b>active</b> to be named on a new or re-pointed route, but an
 * existing route survives its branch being deactivated: the shipments already on it still
 * have to be delivered.
 */
@Slf4j
@Service
public class RouteServiceImpl extends AbstractMasterDataService<Route> implements RouteService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final RouteRepository routes;
    private final BranchLookupPort branches;

    public RouteServiceImpl(RouteRepository routes,
                            BranchLookupPort branches,
                            MasterUniquenessChecker uniqueness,
                            AuditService auditService) {
        super(routes, uniqueness, auditService, "Route", MasterTable.ROUTES);
        this.routes = routes;
        this.branches = branches;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Route create(RouteCommand command) {
        UUID companyId = requireCompany();
        requireBranch(command.bookingBranchId(), companyId, "booking", true);
        requireBranch(command.deliveryBranchId(), companyId, "delivery", true);

        Route route = new Route();
        applyCommonFields(route, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(route, command);
        return createEntity(route);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Route update(UUID id, RouteCommand command) {
        UUID companyId = requireCompany();
        return updateEntity(id, command.expectedVersion(), route -> {
            boolean bookingChanged =
                    !java.util.Objects.equals(route.getBookingBranchId(), command.bookingBranchId());
            boolean deliveryChanged =
                    !java.util.Objects.equals(route.getDeliveryBranchId(), command.deliveryBranchId());
            requireBranch(command.bookingBranchId(), companyId, "booking", bookingChanged);
            requireBranch(command.deliveryBranchId(), companyId, "delivery", deliveryChanged);

            applyCommonFields(route, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(route, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Route getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<Route> search(MasterDataCriteria criteria, Pageable pageable) {
        return doSearch(criteria, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        doDelete(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Route findByBranches(UUID bookingBranchId, UUID deliveryBranchId) {
        UUID companyId = requireCompany();
        return routes.findByCompanyIdAndBookingBranchIdAndDeliveryBranchId(
                        companyId, bookingBranchId, deliveryBranchId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No route runs from branch %s to branch %s."
                                .formatted(bookingBranchId, deliveryBranchId)));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Route activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Route deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(Route route, UUID companyId, UUID excludeId) {
        // One route per ordered pair. Checked including soft-deleted rows, because the
        // unique key does not know about `deleted` and would otherwise reject the insert
        // with a raw constraint violation instead of a 409.
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("booking_branch_id", route.getBookingBranchId());
        pair.put("delivery_branch_id", route.getDeliveryBranchId());
        if (uniqueness.isTaken(tableName, companyId, excludeId, pair)) {
            throw new BusinessRuleException(
                    "A route already exists between these two branches. Edit that one, or "
                            + "create the reverse route if you meant the other direction.");
        }
    }

    @Override
    protected Map<String, Object> snapshot(Route route) {
        Map<String, Object> values = super.snapshot(route);
        values.put("bookingBranchId", String.valueOf(route.getBookingBranchId()));
        values.put("deliveryBranchId", String.valueOf(route.getDeliveryBranchId()));
        values.put("distanceKm", String.valueOf(route.getDistanceKm()));
        values.put("distanceUnit", route.getDistanceUnit());
        values.put("transitDays", route.getTransitDays());
        values.put("transitHours", route.getTransitHours());
        values.put("via", route.getVia());
        return values;
    }

    private void applySpecific(Route route, RouteCommand command) {
        route.setBookingBranchId(command.bookingBranchId());
        route.setDeliveryBranchId(command.deliveryBranchId());
        route.setDistanceKm(command.distanceKm());
        route.setDistanceUnit(command.distanceUnit());
        route.setTransitDays(command.transitDays() == null ? 1 : command.transitDays());
        route.setTransitHours(command.transitHours());
        route.setVia(command.via());
    }

    private void requireBranch(UUID branchId, UUID companyId, String role, boolean mustBeActive) {
        if (branchId == null) {
            throw new BusinessRuleException("A route needs a %s branch.".formatted(role));
        }
        BranchLookupPort.BranchRef branch = branches.findBranch(branchId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No branch of this company has id %s.".formatted(branchId)));
        if (mustBeActive && !branch.active()) {
            throw new BusinessRuleException(
                    "Branch %s is inactive and cannot be the %s end of a route."
                            .formatted(branch.branchCode(), role));
        }
    }
}
