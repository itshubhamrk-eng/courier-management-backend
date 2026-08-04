package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.BranchType;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Input to {@code BranchService.create}. {@code companyId} is absent — the company comes
 * from the caller's JWT. {@code status} is absent — a new branch starts ACTIVE.
 *
 * <p>{@code branchUser} is optional; a null block still yields a user, built from the
 * branch's own details. See {@link NewBranchUser}.
 */
public record CreateBranchCommand(
        String branchCode, String branchName, BranchType branchType,
        String email, String mobile, String alternateMobile, UUID managerId,
        String addressLine1, String addressLine2, String country, String state, String city,
        String district, String taluka, String postalCode,
        BigDecimal latitude, BigDecimal longitude,
        LocalTime openingTime, LocalTime closingTime, String workingDays,
        Boolean allowBooking, Boolean allowDelivery, Boolean allowPickup,
        Boolean allowManifest, Boolean allowCashCollection, Boolean allowWallet,
        String remarks,
        NewBranchUser branchUser
) {

    /**
     * The login account to create with the branch. Every field may be null: the service
     * fills each from the branch, and the address from the branch code as a last resort.
     *
     * @param password null means "generate one and return it once"
     */
    public record NewBranchUser(String email, String firstName, String lastName,
                                String mobile, String password) {
    }
}
