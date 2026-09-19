package com.courier.modules.auth.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * The auth module's view of a user's company-owned role codes (from
 * {@code user_company_roles}) — separate from the legacy JWT-authority {@code Role}
 * enum ({@code user_roles}), which {@code User#roleNames} still supplies.
 *
 * <p>The JWT {@code roles} claim is the union of both: a role assigned only through
 * Role/Permission Management (Booking Operator, Delivery Operator, ...) must still show
 * up there, or every nav leaf gated on {@code roles} (see navigation.config.ts) stays
 * hidden even though the account's {@code permissions} claim is populated correctly.
 * The only implementation is {@code UserCompanyRolesDirectory} in {@code modules/company}.
 */
public interface UserCompanyRolesPort {

    /** Role codes the user holds via user_company_roles. Never null. */
    Set<String> resolveRoleCodes(UUID userId);
}
