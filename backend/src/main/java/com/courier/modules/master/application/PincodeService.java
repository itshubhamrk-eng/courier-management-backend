package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PincodeCommand;
import com.courier.modules.master.domain.Pincode;

/** Use cases for pincodes. See {@link MasterDataService} for the audiences. */
public interface PincodeService extends MasterDataService<Pincode, PincodeCommand> {

    /**
     * The pincode carrying this postal code. Same read audience as {@link #getById}.
     * Added for the Pricing Engine's serviceability check, which is handed a raw pincode
     * (what a booking screen or an external integration actually has) rather than an id.
     *
     * @throws com.courier.shared.exception.ResourceNotFoundException no such pincode
     */
    Pincode findByCode(String code);
}
