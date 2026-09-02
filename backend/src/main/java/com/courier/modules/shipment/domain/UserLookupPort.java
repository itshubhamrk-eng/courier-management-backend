package com.courier.modules.shipment.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What Shipment Booking needs to know about the user who booked a shipment — just a name
 * for the LR receipt's "Created By" line.
 *
 * <p>Shipment owns the interface; {@code modules/company} supplies the adapter — same seam
 * as {@link BranchLookupPort} and, per that port's own class comment, deliberately not a
 * reuse of {@code followup.domain.FollowUpDirectoryPort} or any other module's user port.
 *
 * <p>The company is passed explicitly on every call, never taken from the ambient
 * {@code CompanyContext} — a caller that forgot to bind one gets nothing rather than
 * another company's user.
 */
public interface UserLookupPort {

    /** Identity of one user, flattened to just what the receipt shows. */
    record UserRef(UUID userId, String fullName) {
    }

    /** The user, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<UserRef> findUser(UUID userId, UUID companyId);
}
