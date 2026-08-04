package com.courier.modules.master.application;

import com.courier.modules.master.application.command.WeightSlabCommand;
import com.courier.modules.master.domain.WeightSlab;

/** Use cases for weight slabs. See {@link MasterDataService} for the audiences. */
public interface WeightSlabService extends MasterDataService<WeightSlab, WeightSlabCommand> {
}
