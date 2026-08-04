package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PackageTypeCommand;
import com.courier.modules.master.domain.PackageType;

/** Use cases for package types. See {@link MasterDataService} for the audiences. */
public interface PackageTypeService extends MasterDataService<PackageType, PackageTypeCommand> {
}
