package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreateUserRequest;
import com.courier.modules.company.api.dto.UpdateUserRequest;
import com.courier.modules.company.api.dto.UserResponse;
import com.courier.modules.company.api.dto.UserSearchRequest;
import com.courier.modules.company.api.dto.UserSummaryResponse;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.application.command.CreateUserCommand;
import com.courier.modules.company.application.command.UpdateUserCommand;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wire contract ↔ application/domain types for users.
 *
 * <p>Hand-written, like the project's other mappers. The one thing worth seeing: identity
 * fields ({@code email}, {@code username}, {@code employeeCode}) are mapped <em>in</em>
 * only on create, never on update.
 */
@Component
public class UserMapper {

    public CreateUserCommand toCommand(CreateUserRequest request) {
        return new CreateUserCommand(
                request.employeeCode(), request.employeeId(),
                request.firstName(), request.middleName(), request.lastName(), request.displayName(),
                request.email(), request.username(), request.mobile(), request.alternateMobile(),
                request.password(), request.gender(), request.dateOfBirth(),
                request.designation(), request.department(), request.joiningDate(),
                request.reportingManagerId(), request.branchId(), request.hubId(),
                request.profileImage(), request.remarks(), request.roleIds());
    }

    public UpdateUserCommand toCommand(UpdateUserRequest request) {
        return new UpdateUserCommand(
                request.firstName(), request.middleName(), request.lastName(), request.displayName(),
                request.mobile(), request.alternateMobile(), request.gender(), request.dateOfBirth(),
                request.designation(), request.department(), request.joiningDate(),
                request.reportingManagerId(), request.branchId(), request.hubId(),
                request.profileImage(), request.remarks(), request.version());
    }

    public UserCriteria toCriteria(UserSearchRequest request) {
        UserSearchRequest safe = request == null ? UserSearchRequest.empty() : request;
        return new UserCriteria(
                safe.companyId(), safe.status(), safe.locked(), safe.branchId(), safe.hubId(),
                safe.department(), safe.designation(), safe.roleCode(),
                safe.joinedFrom(), safe.joinedTo(), safe.search());
    }

    public UserResponse toResponse(User user, List<String> roleCodes) {
        return toResponse(user, roleCodes, null);
    }

    public UserResponse toResponse(User user, List<String> roleCodes, Boolean passwordGenerated) {
        return new UserResponse(
                user.getId(), user.getCompanyId(),
                user.getEmployeeCode(), user.getEmployeeId(),
                user.getFirstName(), user.getMiddleName(), user.getLastName(),
                user.effectiveDisplayName(),
                user.getEmail(), user.getUsername(), user.getMobile(), user.getAlternateMobile(),
                user.getGender(), user.getDateOfBirth(), user.getDesignation(), user.getDepartment(),
                user.getJoiningDate(), user.getReportingManagerId(),
                user.getBranchId(), user.getHubId(), user.getProfileImage(),
                user.getStatus(), user.isLocked(), user.getLastLogin(), user.getFailedLoginCount(),
                user.getRemarks(),
                roleCodes == null ? List.of() : roleCodes.stream().sorted().toList(),
                user.getCreatedBy(),
                // Columns are created_at/updated_at; the API says createdDate/updatedDate.
                user.getCreatedAt(), user.getUpdatedBy(), user.getUpdatedAt(),
                user.getVersion(), passwordGenerated);
    }

    public UserResponse toCreatedResponse(UserService.CreatedUser created) {
        return toResponse(created.user(), created.assignedRoleCodes(), created.passwordGenerated());
    }

    public UserSummaryResponse toSummary(User user, int roleCount) {
        return new UserSummaryResponse(
                user.getId(), user.getCompanyId(), user.getEmployeeCode(),
                user.effectiveDisplayName(), user.getEmail(), user.getUsername(), user.getMobile(),
                user.getDesignation(), user.getDepartment(), user.getBranchId(), user.getHubId(),
                user.getStatus(), user.isLocked(), roleCount, user.getCreatedAt(), user.getVersion());
    }
}
