package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;

/** A depot with its assigned branch / vendor resolved for display. */
public record BusDepotView(BusDepot depot, String branchCode, String branchName, String branchType,
                           String divisionName, String parentDepotName) {
}
