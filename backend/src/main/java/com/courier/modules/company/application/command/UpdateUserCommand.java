package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.Gender;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to {@code UserService.update}. Full replacement of the editable profile.
 *
 * <p>Absent by design: {@code employeeCode}, {@code username} and {@code email} are
 * identity and are not changed here (email/username changes have security and login
 * implications that belong in dedicated flows); {@code status} and {@code isLocked} move
 * through their own endpoints; {@code password} through reset. {@code branchId}/{@code
 * hubId} have their own assignment endpoints but are also accepted here for a bulk edit.
 *
 * @param expectedVersion the version last read; a stale value is rejected with 409
 */
public record UpdateUserCommand(
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        String mobile,
        String alternateMobile,
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
        Long expectedVersion
) {
}
