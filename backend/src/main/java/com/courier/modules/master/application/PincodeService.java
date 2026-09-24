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

    /**
     * The Area/City/District names already on file for this pincode's own Area link —
     * same read audience as {@link #getById}, unlike {@link #lookupPostalArea}, since
     * this never touches the postal directory or creates a row.
     *
     * @return empty when no pincode with this code is on file
     */
    java.util.Optional<PincodeGeography> geography(String code);

    /**
     * Same as {@link #geography(String)}, but when {@code areaId} is given, resolves the
     * Area/City/District off that specific Area link instead of the pincode's single fixed
     * "primary" one — for a pincode with several Areas, so a caller that knows which Area
     * was actually selected (e.g. a booked shipment's {@code destinationAreaId}) gets that
     * one back, not always the primary.
     *
     * @return empty when no pincode with this code is on file
     */
    java.util.Optional<PincodeGeography> geography(String code, java.util.UUID areaId);
}
