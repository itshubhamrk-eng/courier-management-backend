package com.courier.modules.master.application;

import com.courier.modules.master.application.command.CityCommand;
import com.courier.modules.master.domain.City;

/** Use cases for cities. See {@link MasterDataService} for the audiences. */
public interface CityService extends MasterDataService<City, CityCommand> {
}
