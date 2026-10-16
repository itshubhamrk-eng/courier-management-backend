package com.courier.modules.platformintegration.application;

import java.util.UUID;

/** What the OCCPL platform may ask of this app: have a company, or stop serving it. */
public interface PlatformTenantService {

    /** Provision (or re-activate) the company; repeated calls return the same company. */
    Result provision(UUID externalCompanyId, ProvisionCommand command);

    /** Deactivate the company if it exists. Returns false when it was never provisioned. Data is kept. */
    boolean deprovision(UUID externalCompanyId);

    /**
     * @param companyCode  the platform's code for the company (becomes this app's company code)
     * @param adminEmail   becomes the first company administrator and the company contact
     */
    record ProvisionCommand(String companyCode, String companyName, String adminEmail,
                            String adminFirstName, String adminLastName, String mobile, Profile profile) {
    }

    /** Optional details the platform holds for the company; any may be null. */
    record Profile(String country, String website, String addressLine1, String addressLine2, String city,
                   String state, String postalCode, String gstNumber, String panNumber, String logoUrl,
                   String faviconUrl, String supportEmail, String supportPhone) {
        static final Profile EMPTY = new Profile(null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    record Result(UUID companyId, boolean created) {
    }
}
