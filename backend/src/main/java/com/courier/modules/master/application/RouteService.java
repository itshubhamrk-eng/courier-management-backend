package com.courier.modules.master.application;

import com.courier.modules.master.application.command.RouteCommand;
import com.courier.modules.master.domain.Route;

import java.util.UUID;

/** Use cases for routes. See {@link MasterDataService} for the audiences. */
public interface RouteService extends MasterDataService<Route, RouteCommand> {

    /**
     * The route between two branches, in that direction. Same read audience as
     * {@link #getById}. Added for Rate Master's calculator, which is handed a booking and
     * a destination branch rather than a route id — see {@code MEMORY/modules/rate-master.md}.
     *
     * @throws com.courier.shared.exception.ResourceNotFoundException no route runs between
     *         these two branches, in this direction, within the caller's company
     */
    Route findByBranches(UUID bookingBranchId, UUID deliveryBranchId);
}
