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

    /**
     * Resolves the Area a raw pincode belongs to via the postal directory, auto-creating
     * whatever State/District/City/Area rows are missing. Same write audience as
     * {@link #create}, not the read one — this can create master rows.
     *
     * @return empty when the directory has no record of this pincode
     */
    java.util.Optional<PincodeAreaLookupResult> lookupPostalArea(String pincode);
}
