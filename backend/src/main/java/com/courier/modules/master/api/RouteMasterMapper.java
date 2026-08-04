package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateRouteRequest;
import com.courier.modules.master.api.dto.RouteResponse;
import com.courier.modules.master.api.dto.UpdateRouteRequest;
import com.courier.modules.master.application.command.RouteCommand;
import com.courier.modules.master.domain.BranchLookupPort;
import com.courier.modules.master.domain.Route;
import com.courier.shared.api.PageResponse;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire contract to application/domain types for routes.
 *
 * <p>The two branch names come through {@link BranchLookupPort}, batched for a page so a
 * hundred routes cost one branch query rather than two hundred. A branch the caller's
 * company does not own is simply absent, so the name stays null.
 */
@Component
@RequiredArgsConstructor
public class RouteMasterMapper {

    private final BranchLookupPort branches;

    public RouteCommand toCommand(CreateRouteRequest r) {
        return new RouteCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.bookingBranchId(), r.deliveryBranchId(), r.distanceKm(), r.distanceUnit(),
                r.transitDays(), r.transitHours(), r.via(), null);
    }

    public RouteCommand toCommand(UpdateRouteRequest r) {
        return new RouteCommand(null, r.name(), r.description(), r.displayOrder(),
                r.bookingBranchId(), r.deliveryBranchId(), r.distanceKm(), r.distanceUnit(),
                r.transitDays(), r.transitHours(), r.via(), r.version());
    }

    public RouteResponse toResponse(Route route) {
        return toResponse(route, branchNames(List.of(route)));
    }

    public RouteResponse toResponse(Route r, Map<UUID, BranchLookupPort.BranchRef> refs) {
        return new RouteResponse(r.getId(), r.getCompanyId(), r.getCode(), r.getName(),
                r.getDescription(), r.getStatus(), r.getDisplayOrder(),
                r.getBookingBranchId(), nameOf(refs, r.getBookingBranchId()),
                r.getDeliveryBranchId(), nameOf(refs, r.getDeliveryBranchId()),
                r.getDistanceKm(), r.getDistanceUnit(), r.getTransitDays(), r.getTransitHours(), r.getVia(),
                r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedBy(), r.getUpdatedAt(), r.getVersion());
    }

    public PageResponse<RouteResponse> toPage(Page<Route> page) {
        Map<UUID, BranchLookupPort.BranchRef> refs = branchNames(page.getContent());
        return PageResponse.from(page, route -> toResponse(route, refs));
    }

    private Map<UUID, BranchLookupPort.BranchRef> branchNames(List<Route> routes) {
        UUID companyId = CompanyContext.getCompanyId().orElse(null);
        if (companyId == null) {
            // A super admin reading across companies has no company bound. Ids are still
            // returned; the names are not guessed from the wrong company.
            return Map.of();
        }
        List<UUID> ids = new ArrayList<>(routes.size() * 2);
        routes.forEach(route -> {
            ids.add(route.getBookingBranchId());
            ids.add(route.getDeliveryBranchId());
        });
        return branches.findBranches(ids, companyId);
    }

    private static String nameOf(Map<UUID, BranchLookupPort.BranchRef> refs, UUID id) {
        BranchLookupPort.BranchRef ref = refs.get(id);
        return ref == null ? null : ref.branchName();
    }
}
