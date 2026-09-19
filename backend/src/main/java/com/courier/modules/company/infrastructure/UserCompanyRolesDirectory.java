package com.courier.modules.company.infrastructure;

import com.courier.modules.auth.application.port.UserCompanyRolesPort;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only implementation of {@link UserCompanyRolesPort} — reads straight off
 * {@link UserRoleRepository}, the same table the Role/Permission Management screens use.
 */
@Component
@RequiredArgsConstructor
public class UserCompanyRolesDirectory implements UserCompanyRolesPort {

    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolveRoleCodes(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(userId).stream()
                .map(UserRole::getRoleCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
