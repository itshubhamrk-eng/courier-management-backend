package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * The account created alongside a branch. Present only on the create response —
 * {@code GET /branches/{id}} never carries it, and never carries a password.
 *
 * <p>{@code temporaryPassword} is populated only when the server generated one. It is
 * returned exactly once, is never logged or audited, and cannot be read back: a lost
 * password is reset through the normal flow.
 */
@Schema(name = "BranchUserResponse", description = "Login account created with the branch")
public record BranchUserResponse(

        UUID userId,
        String email,
        @Schema(description = "Only when the server generated it; show it once, then it is gone")
        String temporaryPassword,
        @Schema(description = "True when this user was also made the branch manager")
        boolean assignedAsManager,
        @Schema(description = "The company role granted to this account — BRANCH_MANAGER")
        UUID roleId,
        @Schema(description = "Code of that role, as it appears in the roles screen")
        String roleCode
) {
}
