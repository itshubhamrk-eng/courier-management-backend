package com.courier.modules.company.infrastructure;

import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.company.application.UserPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * The only implementation of {@link UserPermissionsPort} — delegates straight to
 * {@link UserPermissionService#resolveEffectivePermissionCodes}, the one function that
 * also backs the User Permissions screen, so token issuance and the admin UI can never
 * compute a user's rights differently.
 */
@Component
@RequiredArgsConstructor
public class UserPermissionsDirectory implements UserPermissionsPort {

    private final UserPermissionService userPermissionService;

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolveEffectivePermissions(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return Set.copyOf(userPermissionService.resolveEffectivePermissionCodes(userId));
    }
}
