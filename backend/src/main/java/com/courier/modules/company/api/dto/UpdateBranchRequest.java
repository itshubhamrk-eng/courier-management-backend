package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.BranchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Body of {@code PUT /api/v1/branches/{id}}. Full replacement of the editable fields.
 * {@code branchCode} is immutable, {@code status} and {@code managerId} have their own
 * endpoints. {@code version} is required.
 */
@Schema(name = "UpdateBranchRequest", description = "Full replacement of a branch's editable fields")
public record UpdateBranchRequest(

        @NotBlank @Size(max = 150) String branchName,
        @NotNull BranchType branchType,

        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,
        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

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
        @Size(max = 40) String workingDays,

        Boolean allowBooking, Boolean allowDelivery, Boolean allowPickup,
        Boolean allowManifest, Boolean allowCashCollection, Boolean allowWallet,

        @Size(max = 500) String remarks,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
