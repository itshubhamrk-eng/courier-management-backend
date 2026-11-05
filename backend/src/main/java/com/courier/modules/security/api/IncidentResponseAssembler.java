package com.courier.modules.security.api;

import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.security.api.dto.IncidentResponse;
import com.courier.modules.security.domain.SecurityIncident;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds {@link IncidentResponse}s. Names are resolved with one user lookup and one branch
 * lookup for the whole page (no N+1). Only display fields are copied from {@link User}:
 * the entity itself, and so its password hash, never reaches a response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentResponseAssembler {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    public IncidentResponse toResponse(SecurityIncident incident) {
        return toResponses(List.of(incident)).get(0);
    }

    public List<IncidentResponse> toResponses(List<SecurityIncident> incidents) {
        Set<UUID> userIds = new HashSet<>();
        for (SecurityIncident i : incidents) {
            if (i.getUserId() != null) userIds.add(i.getUserId());
            if (i.getResolvedBy() != null) userIds.add(i.getResolvedBy());
        }
        Map<UUID, User> usersById = userIds.isEmpty() ? Map.of()
                : userRepository.findAllByIdIn(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Set<UUID> branchIds = usersById.values().stream()
                .map(User::getBranchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> branchNames = branchIds.isEmpty() ? Map.of() : namesOf(branchRepository.findAllByIdIn(branchIds));

        return incidents.stream().map(i -> assemble(i, usersById, branchNames)).toList();
    }

    private Map<UUID, String> namesOf(Collection<Branch> branches) {
        Map<UUID, String> names = new HashMap<>();
        branches.forEach(b -> names.put(b.getId(), b.getBranchName()));
        return names;
    }

    private IncidentResponse assemble(SecurityIncident i, Map<UUID, User> users, Map<UUID, String> branchNames) {
        User target = i.getUserId() == null ? null : users.get(i.getUserId());
        User resolver = i.getResolvedBy() == null ? null : users.get(i.getResolvedBy());
        JsonNode evidence = JsonNodeFactory.instance.objectNode();
        boolean unreadable = false;
        if (i.getEvidence() != null && !i.getEvidence().isBlank()) {
            try {
                evidence = objectMapper.readTree(i.getEvidence());
            } catch (Exception e) {
                log.warn("Unreadable evidence on security incident {}: {}", i.getId(), e.toString());
                unreadable = true;
            }
        }
        return new IncidentResponse(
                i.getId(), i.getCompanyId(), i.getRule(), i.getSeverity(), i.getStatus(), i.getAutoAction(),
                i.getDetectedAt(),
                i.getUserId(),
                target == null ? null : target.displayName(),
                target == null ? null : target.getEmail(),
                target == null ? List.of() : target.roleNames().stream().sorted().toList(),
                target == null || target.getBranchId() == null ? null : branchNames.get(target.getBranchId()),
                evidence, unreadable,
                i.getResolvedBy(),
                resolver == null ? null : resolver.displayName(),
                i.getResolvedAt(), i.getResolutionNote());
    }
}
