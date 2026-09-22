package com.courier.shared.activity.api;

import com.courier.shared.activity.api.dto.ActivityLogResponse;
import com.courier.shared.activity.api.dto.ActivityLogSearchRequest;
import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogCriteria;
import org.springframework.stereotype.Component;

@Component
public class ActivityLogMapper {

    public ActivityLogCriteria toCriteria(ActivityLogSearchRequest r) {
        if (r == null) {
            return new ActivityLogCriteria(null, null, null, null, null, null, null, null, null, null, null);
        }
        return new ActivityLogCriteria(null, r.userId(), r.module(), r.action(), r.entityType(), r.entityId(),
                r.status(), r.sessionId(), r.dateFrom(), r.dateTo(), r.search());
    }

    public ActivityLogResponse toResponse(ActivityLog a) {
        return new ActivityLogResponse(a.getId(), a.getCompanyId(), a.getUserId(), a.getUsername(), a.getModule(),
                a.getSubmodule(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDescription(),
                a.getOldValue(), a.getNewValue(), a.getIpAddress(), a.getDevice(), a.getBrowser(), a.getOs(),
                a.getSessionId(), a.getRequestMethod(), a.getApiEndpoint(), a.getStatus(), a.getErrorMessage(),
                a.getOccurredAt());
    }
}
