package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateUserCommand;
import com.courier.modules.company.application.command.UpdateUserCommand;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserCriteria;
import com.courier.modules.company.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for company users.
 *
 * <p><b>Audiences and their reach:</b>
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — full management, own company only.</li>
 *   <li>{@code SUPER_ADMIN} — read across every company, no writes. Administering a
 *       company's staff is the company's job; doing it for them would look like a
 *       compromise.</li>
 *   <li>{@code BRANCH_MANAGER} / {@code HUB_MANAGER} — read only, and only the users
 *       placed at their own branch / hub. Enforced in the implementation, since no URL
 *       rule can express "their branch".</li>
 * </ul>
 *
 * <p>Returns entities; the wire contract belongs to the {@code api} layer.
 */
public interface UserService {

    /** Result of a creation — carries what the caller cannot read back afterwards. */
    record CreatedUser(User user, boolean passwordGenerated, List<String> assignedRoleCodes) {
    }

    CreatedUser create(CreateUserCommand command);

    User update(UUID id, UpdateUserCommand command);

    User getById(UUID id);

    Page<User> search(UserCriteria criteria, Pageable pageable);

    /** Soft delete. The row is retained; the account can no longer authenticate. */
    void delete(UUID id);

    /** Role counts for several users at once, so a list page avoids an N+1 per row. */
    java.util.Map<UUID, Integer> roleCounts(java.util.Collection<UUID> userIds);

    User activate(UUID id);

    User deactivate(UUID id);

    User lock(UUID id, String reason);

    User unlock(UUID id);

    /** Admin reset: sets a new password without knowing the old one. */
    void resetPassword(UUID id, String newPassword, boolean mustChangeOnNextLogin);

    /** Self-service change: the current password must be supplied and verified. */
    void changePassword(UUID id, String currentPassword, String newPassword);

    List<UserRole> assignRole(UUID id, UUID roleId);

    void removeRole(UUID id, UUID roleId);

    User assignBranch(UUID id, UUID branchId);

    User assignHub(UUID id, UUID hubId);

    /** The company roles a user holds. */
    List<UserRole> listRoles(UUID id);
}
