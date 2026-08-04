package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PaymentModeCommand;
import com.courier.modules.master.domain.PaymentMode;

/** Use cases for payment modes. See {@link MasterDataService} for the audiences. */
public interface PaymentModeService extends MasterDataService<PaymentMode, PaymentModeCommand> {
}
