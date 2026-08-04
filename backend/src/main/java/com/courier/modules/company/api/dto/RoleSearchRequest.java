package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/roles}, bound as a parameter object.
 *
 * <p>Every field is optional and filters combine with {@code AND}. Paging and sorting are
 * not here — Spring's {@code Pageable} resolves {@code page}, {@code size} and
 * {@code sort} from the same query string.
 *
 * <p>{@code companyId} is only meaningful for a {@code SUPER_ADMIN} narrowing a
 * platform-wide listing. For a {@code COMPANY_ADMIN} it is **ignored**: the service pins
 * the criteria to their own company, so supplying someone else's id narrows the result to
 * nothing rather than widening it to another company's roles.
 */
@Schema(name = "RoleSearchRequest", description = "Role search filters")
public record RoleSearchRequest(

        @Schema(description = "SUPER_ADMIN only — restrict a platform-wide listing to one "
                + "company. Ignored for a COMPANY_ADMIN.")
        UUID companyId,

        RoleStatus status,

        @Schema(description = "Match any of these, e.g. `roleType=OPERATIONS&roleType=FINANCE`")
        Set<RoleType> roleType,

        @Schema(description = "true for the seeded roles, false for the company's own")
        Boolean isSystemRole,

        @Schema(description = "true for the role new users receive by default")
        Boolean isDefault,

        @Schema(description = "Roles granting this permission code, e.g. `SHIPMENT_DELETE`")
        String permissionCode,

        @Size(max = 100)
        @Schema(description = "Free text over code, name and description. `%`, `_` and `\\` "
                + "match themselves — role codes contain underscores, so they are escaped "
                + "rather than rejected.")
        String search
) {

    public static RoleSearchRequest empty() {
        return new RoleSearchRequest(null, null, null, null, null, null, null);
    }
}
