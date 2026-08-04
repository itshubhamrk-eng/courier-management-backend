package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.Gender;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Input to {@code UserService.create}.
 *
 * <p>{@code companyId} is absent: the company comes from the caller's verified JWT, never
 * the request body. {@code status} is absent too — a new user starts PENDING and moves
 * through the lifecycle endpoints.
 *
 * <p>{@code password} may be null: when omitted the account is created with an unusable
 * random password and must go through the reset flow, exactly like a provisioned admin.
 * {@code roleIds} may be empty — the company's default role is applied.
 */
public record CreateUserCommand(
        String employeeCode,
        String employeeId,
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        String email,
        String username,
        String mobile,
        String alternateMobile,
        String password,
        Gender gender,
        LocalDate dateOfBirth,
        String designation,
        String department,
        LocalDate joiningDate,
        UUID reportingManagerId,
        UUID branchId,
        UUID hubId,
        String profileImage,
        String remarks,
        List<UUID> roleIds
) {
}
