package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.CompanyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full representation of a company.
 *
 * <p>Nulls are serialised rather than dropped, so a client can tell "not set" from
 * "field missing"; the global Jackson setting omits nulls, hence the explicit
 * {@code @JsonInclude(ALWAYS)}.
 *
 * <p>{@code provisioning} is populated only in the response to a creation — it reports
 * what was set up alongside the company, whether the activation email went out, and the
 * administrator's <b>temporary password</b>. That password is readable here and nowhere
 * else, ever; every other response for this company omits the block entirely.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CompanyResponse", description = "A company in full")
public record CompanyResponse(

        UUID id,

        @Schema(description = "The ownership key: stamped on every row this company owns "
                + "and carried in the JWT `cid` claim")
        UUID companyId,

        String companyCode,
        String companyName,
        String legalName,
        String displayName,

        UUID subscriptionPlanId,
        CompanyStatus status,

        LocalDate trialStartDate,
        LocalDate trialEndDate,
        LocalDate subscriptionStartDate,
        LocalDate subscriptionEndDate,

        String email,
        String mobile,
        String alternateMobile,
        String website,

        String gstNumber,
        String panNumber,
        String cinNumber,

        String logo,
        String favicon,

        String addressLine1,
        String addressLine2,
        String country,
        String state,
        String city,
        String postalCode,

        String timezone,
        String currency,
        String language,
        String dateFormat,
        String timeFormat,

        @Schema(description = "Derived from status: true while the company may operate")
        boolean isActive,

        String remarks,

        UUID createdBy,
        Instant createdDate,
        UUID updatedBy,
        Instant updatedDate,

        @Schema(description = "Echo this back in a PUT to detect concurrent edits")
        Long version,

        @Schema(description = "Present only in the response to a creation")
        ProvisioningSummary provisioning
) {

    /**
     * What was created alongside the company.
     *
     * <p>{@code temporaryPassword} appears here and nowhere else, ever. It is not
     * logged, not audited, not emailed, and not readable from any later request — a
     * lost one is reset, not recovered. Show it once, to the operator who created the
     * company, and tell them so.
     *
     * @param adminUserId         the first Company Admin
     * @param adminEmail          their login address
     * @param temporaryPassword   the generated password, readable exactly once
     * @param activationEmailSent false means the account exists but the link must be
     *                            reissued before anyone can sign in — surface it
     * @param roleCount           default roles seeded
     * @param settingCount        default settings seeded
     */
    @Schema(name = "CompanyProvisioningSummary")
    public record ProvisioningSummary(UUID adminUserId,
                                      String adminEmail,
                                      @Schema(description = "Shown once. Never retrievable again.")
                                      String temporaryPassword,
                                      boolean activationEmailSent,
                                      int roleCount,
                                      int settingCount) {
    }
}
