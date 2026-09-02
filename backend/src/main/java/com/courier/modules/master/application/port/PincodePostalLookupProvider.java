package com.courier.modules.master.application.port;

import java.util.List;

/**
 * Resolves a raw pincode to the real post office(s) that serve it, so the Pincode create
 * form can fetch and auto-select an Area instead of asking the operator to hunt for one.
 *
 * <p>An abstraction rather than a direct HTTP call in the service, the same "future ready"
 * seam {@code EwayBillProvider}/{@code WhatsAppProvider} use: a company that later
 * subscribes to a paid, more accurate postal directory swaps the implementation with no
 * change to {@code PincodeServiceImpl}.
 */
public interface PincodePostalLookupProvider {

    /**
     * @param pincode 4-10 digits, already validated by the caller
     * @return every post office India Post's own directory lists for this pincode, in the
     *         order the upstream source returned them (empty, never null, when the pincode
     *         is unknown or the lookup could not be performed)
     */
    List<PostOffice> lookup(String pincode);

    /**
     * One post office. {@code name} is the locality/post-office label this module treats
     * as an Area's name; {@code division} is the closest field India Post's directory
     * offers to a "city" (there is no separate city field in that data) and falls back to
     * {@code district} when blank.
     */
    record PostOffice(String name, String division, String district, String state, String country) {
    }
}
