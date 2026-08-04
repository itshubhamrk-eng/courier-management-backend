package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.domain.Vehicle;

import java.util.List;
import java.util.UUID;

/**
 * Fleet management, minimal by design — see {@code Vehicle}'s own class-level note.
 * {@code COMPANY_ADMIN}/{@code BRANCH_MANAGER} write, any authenticated company user
 * reads (the Dispatch desk needs the list to populate its picker), matching every other
 * module ahead of the authorise-on-permissions capstone.
 */
public interface VehicleService {

    Vehicle create(CreateVehicleCommand command);

    Vehicle getById(UUID id);

    /** Active vehicles only, ordered by vehicle number — exactly what a picker needs. */
    List<Vehicle> listActive();

    List<Vehicle> listAll();

    Vehicle activate(UUID id);

    Vehicle deactivate(UUID id);
}
