package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/companies/{id}}.
 *
 * <p>A full replacement, not a patch: an omitted optional field is written as null.
 *
 * <p>Absent by design: {@code companyCode} and {@code companyId} are immutable, and
 * {@code status} moves only through the activate, suspend and expire endpoints so every
 * transition is validated and separately audited.
 */
@Schema(name = "UpdateCompanyRequest", description = "Full replacement of a company")
public record UpdateCompanyRequest(

        @NotBlank @Size(max = 150) String companyName,
        @Size(max = 200) String legalName,
        @Size(max = 100) String displayName,

        @NotNull
        @Schema(description = "Moving a company to another plan re-prices it immediately. "
                + "Existing seeded settings are not rewritten.")
        UUID subscriptionPlanId,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,

        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        @Size(max = 255)
        @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://")
        String website,

        @Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z][Zz][0-9A-Za-z]$",
                message = "must be a valid 15-character GSTIN")
        String gstNumber,

        @Pattern(regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$",
                message = "must be a valid 10-character PAN")
        String panNumber,

        @Size(max = 21) String cinNumber,
        @Size(max = 500) String logo,
        @Size(max = 500) String favicon,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @Size(max = 64) String timezone,

        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be a 3-letter ISO-4217 code")
        String currency,

        @Size(max = 10) String language,
        @Size(max = 20) String dateFormat,
        @Size(max = 20) String timeFormat,
        @Size(max = 500) String remarks,

        @Schema(description = "Extending a trial is done here; expiring one is not")
        LocalDate trialEndDate,

        LocalDate subscriptionStartDate,
        LocalDate subscriptionEndDate,

        /*
         * Mandatory. Without the version the client last read, two admins editing the
         * same company would both succeed and the second would silently discard the
         * first one's changes.
         */
        @NotNull
        @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.",
                example = "2")
        Long version
) {
}
