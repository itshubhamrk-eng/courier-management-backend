package com.courier.modules.master.application;

import com.courier.modules.master.application.command.StateCommand;
import com.courier.modules.master.domain.State;

/** Use cases for states. See {@link MasterDataService} for the audiences. */
public interface StateService extends MasterDataService<State, StateCommand> {
}
