package com.courier.modules.master.application;

import com.courier.modules.master.application.command.CountryCommand;
import com.courier.modules.master.domain.Country;

/** Use cases for countries. See {@link MasterDataService} for the audiences. */
public interface CountryService extends MasterDataService<Country, CountryCommand> {
}
