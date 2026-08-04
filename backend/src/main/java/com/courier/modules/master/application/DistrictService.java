package com.courier.modules.master.application;

import com.courier.modules.master.application.command.DistrictCommand;
import com.courier.modules.master.domain.District;

/** Use cases for districts. See {@link MasterDataService} for the audiences. */
public interface DistrictService extends MasterDataService<District, DistrictCommand> {
}
