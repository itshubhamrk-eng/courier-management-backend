package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The company an incident belongs to: the user's home company ({@code users.company_id}),
 * not whichever company an activity row happened to be stamped with (a platform user acting
 * across companies has rows in several). One batched lookup per scan; a user that cannot be
 * found keeps the company from its own rows.
 */
final class HomeCompany {

    private HomeCompany() {
    }

    static Map<UUID, UUID> of(UserRepository userRepository, Collection<UUID> userIds) {
        Map<UUID, UUID> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (User user : userRepository.findAllByIdIn(userIds)) {
            if (user.getCompanyId() != null) {
                out.put(user.getId(), user.getCompanyId());
            }
        }
        return out;
    }
}
