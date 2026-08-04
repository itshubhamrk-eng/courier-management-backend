package com.courier.modules.master.application;

import com.courier.modules.master.application.command.VehicleTypeCommand;
import com.courier.modules.master.domain.VehicleType;

/** Use cases for vehicle types. See {@link MasterDataService} for the audiences. */
public interface VehicleTypeService extends MasterDataService<VehicleType, VehicleTypeCommand> {
}
