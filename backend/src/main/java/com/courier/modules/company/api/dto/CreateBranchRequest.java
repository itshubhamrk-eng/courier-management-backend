package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.BranchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/branches}. {@code COMPANY_ADMIN} only.
 *
 * <p>Not accepted: {@code companyId} (from the JWT), {@code status} (a new branch starts
 * ACTIVE). {@code managerId}, if given, must be a user of the company. Omitted {@code
 * allow*} flags default to true except wallet, which defaults to false.
 *
 * <p>One call creates three things: the branch, its login account ({@code branchUser},
 * optional — a derived account is created when the block is absent) and, from the
 * {@code BranchCreated} event, its wallet.
 */
@Schema(name = "CreateBranchRequest", description = "New branch within the caller's company")
public record CreateBranchRequest(

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$",
                message = "3-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "PUNE_MAIN") String branchCode,

        @NotBlank @Size(max = 150) @Schema(example = "Pune Main Branch") String branchName,

        @NotNull BranchType branchType,

        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,
        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        UUID managerId,

        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 100) String taluka,
        @Size(max = 20) String postalCode,

        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,

        LocalTime openingTime,
        LocalTime closingTime,
        @Size(max = 40)
        @Schema(description = "Uppercase CSV of MON..SUN", example = "MON,TUE,WED,THU,FRI,SAT")
        String workingDays,

        Boolean allowBooking, Boolean allowDelivery, Boolean allowPickup,
        Boolean allowManifest, Boolean allowCashCollection, Boolean allowWallet,
        @Schema(description = "Credit branch commission to the wallet the instant a PREPAID "
                + "booking debit settles. Defaults to true when omitted.")
        Boolean instantCommission,

        @Size(max = 500) String remarks,

        @Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z]Z[0-9A-Za-z]$",
                message = "must be a valid 15-character GSTIN")
        @Schema(description = "Branch's GSTIN. Optional.", example = "27AAAAA0000A1Z5") String gstNumber,
        @Pattern(regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "must be a valid 10-character PAN")
        @Schema(description = "Branch's PAN. Optional.", example = "AAAAA0000A") String panNumber,

        @DecimalMin("0.0") @DecimalMax("100.0")
        @Schema(description = "GST percentage. Defaults to 18 when omitted.", example = "18")
        BigDecimal gstPercentage,
        @DecimalMin("0.0") @DecimalMax("100.0")
        @Schema(description = "Company's commission percentage on other charges. Defaults to 20 when omitted.", example = "20")
        BigDecimal commissionOnOtherCharges,
        @DecimalMin("0.0") @DecimalMax("100.0")
        @Schema(description = "Commission percentage on basic freight. Defaults to 10 when omitted.", example = "10")
        BigDecimal commissionOnBasicFreight,
        @DecimalMin("0.0") @DecimalMax("100.0")
        @Schema(description = "Company service charge percentage. Defaults to 10 when omitted.", example = "10")
        BigDecimal companyServiceChargePercentage,
        @DecimalMin("0.0")
        @Schema(description = "DRS charge per item quantity, debited on delivery "
                + "(drsCharge = drsChargePerQty * qty). Defaults to 2 when omitted.", example = "2")
        BigDecimal drsChargePerQty,

        @Valid
        @Schema(description = "The branch's login account. Omit to have one derived from "
                + "the branch code.")
        BranchUserRequest branchUser
) {
}
