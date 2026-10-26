package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.shared.activity.application.ActivityActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ActivityActorResolverImpl implements ActivityActorResolver {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ActivityActor> resolve(Collection<UUID> userIds) {
        Set<UUID> ids = new HashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        // JPQL, not findAllById: the company filter applies to queries.
        List<User> users = userRepository.findAllByIdIn(ids);
        Set<UUID> branchIds = new HashSet<>();
        for (User u : users) {
            if (u.getBranchId() != null) {
                branchIds.add(u.getBranchId());
            }
        }
        Map<UUID, String> branchNames = new HashMap<>();
        if (!branchIds.isEmpty()) {
            for (Branch b : branchRepository.findAllByIdIn(branchIds)) {
                branchNames.put(b.getId(), b.getBranchName());
            }
        }
        Map<UUID, ActivityActor> actors = new HashMap<>();
        for (User u : users) {
            actors.put(u.getId(), new ActivityActor(u.displayName(), branchNames.get(u.getBranchId())));
        }
        return actors;
    }
}
