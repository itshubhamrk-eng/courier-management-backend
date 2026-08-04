package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/users}, bound as a parameter object. Paging and
 * sorting come from Spring's {@code Pageable} on the same query string.
 *
 * <p>{@code companyId} is meaningful only for a {@code SUPER_ADMIN}. For a
 * {@code COMPANY_ADMIN} it is overridden to their own company; for a branch or hub
 * manager the result is further pinned to their placement, whatever is passed here.
 */
@Schema(name = "UserSearchRequest", description = "User search filters")
public record UserSearchRequest(
        UUID companyId,
        Set<UserStatus> status,
        Boolean locked,
        UUID branchId,
        UUID hubId,
        @Size(max = 100) String department,
        @Size(max = 100) String designation,
        @Schema(description = "Users holding this company role code") String roleCode,
        LocalDate joinedFrom,
        LocalDate joinedTo,
        @Size(max = 100)
        @Schema(description = "Free text over name, email, username, employee code, mobile")
        String search
) {
        public static UserSearchRequest empty() {
                return new UserSearchRequest(null, null, null, null, null, null, null, null,
                        null, null, null);
        }
}
