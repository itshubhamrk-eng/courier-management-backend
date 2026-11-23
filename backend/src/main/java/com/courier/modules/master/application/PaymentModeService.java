package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PaymentModeCommand;
import com.courier.modules.master.domain.PaymentMode;
import java.util.UUID;

/** Use cases for payment modes. See {@link MasterDataService} for the audiences. */
public interface PaymentModeService extends MasterDataService<PaymentMode, PaymentModeCommand> {

    /** System path only (scheduler threads with no logged-in user): no authorization, the
     *  caller passes the company id and is responsible for it being right. Empty, rather
     *  than an exception, for an unknown id: a throw inside a caller's transaction marks it
     *  rollback-only even when the caller catches it. */
    java.util.Optional<PaymentMode> findByIdUnguarded(UUID companyId, UUID id);
}
