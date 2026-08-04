package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * The query parameters every master list accepts, bound as a parameter object.
 *
 * <p>One record for twelve endpoints. The filters that belong to a single list — a parent
 * id, a zone, a weight unit — are declared as explicit {@code @RequestParam}s on the
 * controller that has them, which keeps each list's Swagger page showing exactly its own
 * filters instead of a union of everyone's.
 *
 * <p>{@code companyId} is meaningful only for a {@code SUPER_ADMIN} reading across
 * companies. For a company user it is overridden with their own, never honoured —
 * decision 27 in {@code MEMORY/AI_CONTEXT.md}.
 */
@Schema(name = "MasterSearchRequest", description = "Filters common to every master list")
public record MasterSearchRequest(
        UUID companyId,
        Set<MasterStatus> status,
        @Size(max = 100)
        @Schema(description = "Free text over code, name and description")
        String search
) {

    public static MasterSearchRequest empty() {
        return new MasterSearchRequest(null, null, null);
    }
}
