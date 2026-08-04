package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/companies}, bound as a parameter object.
 *
 * <p>Every field is optional and filters combine with {@code AND}. A record rather than
 * eleven method parameters: the controller signature stays readable and springdoc
 * documents each field from its own annotations.
 *
 * <p>Paging and sorting are not here — Spring's {@code Pageable} already resolves
 * {@code page}, {@code size} and {@code sort} from the same query string.
 */
@Schema(name = "CompanySearchRequest", description = "Company search filters")
public record CompanySearchRequest(

        @Schema(description = "Match any of these statuses, e.g. `status=TRIAL&status=ACTIVE`")
        Set<CompanyStatus> status,

        @Schema(description = "The derived operational flag")
        Boolean isActive,

        UUID subscriptionPlanId,

        @Size(max = 100) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,

        @Schema(description = "Trial or subscription ending on or before this date — "
                + "the renewals worklist", example = "2026-09-30")
        LocalDate expiringBefore,

        @Schema(example = "2026-01-01") LocalDate createdFrom,
        @Schema(example = "2026-12-31") LocalDate createdTo,

        @Size(max = 100)
        @Schema(description = "Free text over code, name, legal name, email and mobile. "
                + "`%`, `_` and `\\` match themselves — CompanySpecifications escapes them "
                + "rather than rejecting them, because company codes legitimately contain "
                + "underscores.")
        String search
) {

    public static CompanySearchRequest empty() {
        return new CompanySearchRequest(null, null, null, null, null, null, null, null, null, null);
    }
}
