package com.courier.modules.platformintegration.application;

import java.util.Set;
import java.util.UUID;

/** Asks the OCCPL platform whether a person may use this product for a company, and with which company roles. */
public interface CoreAccessClient {

    /** @param reason the platform's reason code (OK when allowed); never shown to the person */
    record Decision(boolean allowed, String reason, Set<String> roles) {
        public static Decision denied(String reason) {
            return new Decision(false, reason, Set.of());
        }
    }

    /**
     * @param platformCompanyId the company's id on the platform
     * @param userSubject       the {@code sub} claim of the person's platform token
     * @throws IllegalStateException when the platform cannot be reached or does not answer properly
     */
    Decision check(UUID platformCompanyId, String userSubject);
}
