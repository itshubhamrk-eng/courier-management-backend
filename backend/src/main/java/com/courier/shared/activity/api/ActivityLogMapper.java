package com.courier.shared.activity.api;

import com.courier.shared.activity.api.dto.ActivityLogResponse;
import com.courier.shared.activity.api.dto.ActivityLogSearchRequest;
import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogCriteria;
import com.courier.shared.activity.application.ActivityActorResolver;
import com.courier.shared.activity.application.ActivityActorResolver.ActivityActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivityLogMapper {

    private final ActivityActorResolver actorResolver;

    public ActivityLogCriteria toCriteria(ActivityLogSearchRequest r) {
        if (r == null) {
            return new ActivityLogCriteria(null, null, null, null, null, null, null, null, null, null, null);
        }
        return new ActivityLogCriteria(null, r.userId(), r.module(), r.action(), r.entityType(), r.entityId(),
                r.status(), r.sessionId(), r.dateFrom(), r.dateTo(), r.search());
    }

    public ActivityLogResponse toResponse(ActivityLog a) {
        return toResponse(a, actorResolver.resolve(Collections.singletonList(a.getUserId())));
    }

    /** One user/branch lookup for the whole page instead of one per row. */
    public Page<ActivityLogResponse> toResponsePage(Page<ActivityLog> page) {
        Map<UUID, ActivityActor> actors = actorResolver.resolve(
                page.getContent().stream().map(ActivityLog::getUserId).distinct().toList());
        return page.map(a -> toResponse(a, actors));
    }

    private ActivityLogResponse toResponse(ActivityLog a, Map<UUID, ActivityActor> actors) {
        ActivityActor actor = a.getUserId() == null ? null : actors.get(a.getUserId());
        return new ActivityLogResponse(a.getId(), a.getCompanyId(), a.getUserId(), a.getUsername(),
                actor == null ? null : actor.fullName(), actor == null ? null : actor.branchName(), a.getModule(),
                a.getSubmodule(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDescription(),
                a.getOldValue(), a.getNewValue(), a.getIpAddress(), a.getDevice(), a.getBrowser(), a.getOs(),
                a.getSessionId(), a.getRequestMethod(), a.getApiEndpoint(), a.getStatus(), a.getErrorMessage(),
                a.getOccurredAt());
    }
}
