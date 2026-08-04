package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/companies}.
 *
 * <p>Bean Validation covers shape only. Everything conditional — the plan must exist and
 * be active, the code and tax numbers must be unique — is enforced in the service, where
 * it holds for every write path rather than only for HTTP.
 *
 * <p>Neither {@code companyId} nor {@code status} is accepted: the first is generated,
 * the second follows from the plan's trial period. A caller that could choose either
 * would be choosing another company's data namespace, or a free subscription.
 */
@Schema(name = "CreateCompanyRequest", description = "New company")
public record CreateCompanyRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,48}[A-Za-z0-9]$",
                message = "must be 3-50 characters of letters, digits, hyphen or underscore")
        @Schema(description = "Stable machine key, uppercased on save. Immutable afterwards.",
                example = "ACME_LOGISTICS")
        String companyCode,

        @NotBlank @Size(max = 150)
        @Schema(example = "Acme Logistics")
        String companyName,

        @Size(max = 200)
        @Schema(description = "Registered entity name as it appears on invoices",
                example = "Acme Logistics Private Limited")
        String legalName,

        @Size(max = 100)
        @Schema(description = "Short name for UI headers. Defaults to companyName.",
                example = "Acme")
        String displayName,

        @NotNull
        @Schema(description = "Must reference an active subscription plan")
        UUID subscriptionPlanId,

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "Primary contact, and the login address of the first admin "
                + "unless adminEmail is supplied",
                example = "ops@acme-logistics.com")
        String email,

        @NotBlank
        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        @Schema(example = "+91 9876543210")
        String mobile,

        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        @Size(max = 255)
        @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://")
        String website,

        @Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z][Zz][0-9A-Za-z]$",
                message = "must be a valid 15-character GSTIN")
        @Schema(example = "27AAPFU0939F1ZV")
        String gstNumber,

        @Pattern(regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$",
                message = "must be a valid 10-character PAN")
        @Schema(example = "AAPFU0939F")
        String panNumber,

        @Size(max = 21)
        @Schema(example = "U74999MH2015PTC123456")
        String cinNumber,

        @Size(max = 500)
        @Schema(description = "URL of the logo. Binary assets live in object storage, not here.")
        String logo,

        @Size(max = 500)
        String favicon,

        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,

        @Size(max = 64)
        @Schema(description = "IANA timezone id. Defaults to Asia/Kolkata.", example = "Asia/Kolkata")
        String timezone,

        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be a 3-letter ISO-4217 code")
        @Schema(description = "Defaults to the subscription plan's currency", example = "INR")
        String currency,

        @Size(max = 10)
        @Schema(description = "Defaults to en", example = "en")
        String language,

        @Size(max = 20) String dateFormat,
        @Size(max = 20) String timeFormat,
        @Size(max = 500) String remarks,

        @Schema(description = "When billing starts. Defaults to the end of the trial, "
                + "or today when the plan has no trial.")
        LocalDate subscriptionStartDate,

        @Email @Size(max = 255)
        @Schema(description = "Login address for the first Company Admin. Defaults to `email`.")
        String adminEmail,

        @Size(max = 100) String adminFirstName,
        @Size(max = 100) String adminLastName,

        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String adminMobile
) {
}
