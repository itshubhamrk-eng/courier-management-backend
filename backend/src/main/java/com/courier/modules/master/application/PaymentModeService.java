package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PaymentModeCommand;
import com.courier.modules.master.domain.PaymentMode;
import java.util.UUID;

/** Use cases for payment modes. See {@link MasterDataService} for the audiences. */
public interface PaymentModeService extends MasterDataService<PaymentMode, PaymentModeCommand> {

    /** System path only (scheduler threads with no logged-in user): no authorization, the
     *  caller passes the company id and is responsible for it being right. */
    PaymentMode getByIdUnguarded(UUID companyId, UUID id);
}
