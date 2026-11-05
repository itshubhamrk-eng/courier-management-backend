package com.courier.modules.security.application.scan.rules;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.SessionIpSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One session whose requests in the window came from two or more distinct client IPs. */
@Component
@RequiredArgsConstructor
public class SessionMultiIpRule implements ScanRule {

    /** Evidence lists at most this many IPs. */
    private static final int MAX_IPS_IN_EVIDENCE = 5;

    private final ActivityLogRepository activityLogRepository;

    @Override
    public IncidentRule code() {
        return IncidentRule.SESSION_MULTI_IP;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        Map<UUID, IncidentRequest> byUser = new LinkedHashMap<>();
        for (SessionIpSummary summary : activityLogRepository.findMultiIpSessions(from, to)) {
            if (byUser.containsKey(summary.userId())) {
                continue;
            }
            List<String> ips = activityLogRepository.findDistinctIps(
                    summary.sessionId(), from, to, PageRequest.of(0, MAX_IPS_IN_EVIDENCE));
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sessionId", summary.sessionId().toString());
            evidence.put("distinctIpCount", summary.distinctIpCount());
            evidence.put("ips", ips);
            evidence.put("windowFrom", from.toString());
            evidence.put("windowTo", to.toString());
            byUser.put(summary.userId(),
                    new IncidentRequest(summary.companyId(), summary.userId(), IncidentRule.SESSION_MULTI_IP, evidence));
        }
        return List.copyOf(byUser.values());
    }
}
