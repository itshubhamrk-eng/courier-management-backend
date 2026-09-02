package com.courier.modules.master.infrastructure;

import com.courier.modules.master.application.port.PincodePostalLookupProvider;

import java.util.List;

/**
 * The deployment has opted out of the outbound call (see {@code PincodeLookupConfig}).
 * Always empty, never guessed — the create form falls back to the manual Area picker.
 */
public class DisabledPincodePostalLookupProvider implements PincodePostalLookupProvider {

    @Override
    public List<PostOffice> lookup(String pincode) {
        return List.of();
    }
}
