package com.courier.modules.security.application.scan.rules;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.UserActionCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One user doing an unusual volume of destructive or pricing actions. Flag-only.
 *
 * <p>What it measures: {@code activity_logs} rows with {@code status = SUCCESS} in the scan
 * window, per user, as written by {@code ActivityLoggingFilter} (module = the URL's first
 * segment title-cased, unless a service set it through {@code ActivityContext}; action = a
 * trailing keyword or the HTTP verb). Fires when the user's total is MORE than
 * {@code bulkActivity.threshold}. Exact values matched:
 * <ul>
 *   <li>action {@code CANCEL}, any module: written today by
 *       {@code POST /shipments/{id}/cancel} (module {@code Shipments}),
 *       {@code POST /eway-bills/{id}/cancel} ({@code Eway Bills}),
 *       {@code PATCH /pickups/{id}/cancel} ({@code Pickups}) and
 *       {@code POST /customer-invoices/{id}/cancel} ({@code Customer Invoices}).</li>
 *   <li>action {@code EXPORT}, any module: <b>not written by any endpoint today</b> (the
 *       only {@code /export} route is under {@code /api/v1/activity-logs}, which the filter
 *       excludes); matched so a future export endpoint counts without a rule change.</li>
 *   <li>rate edits: module {@code Rates}, {@code Customer Rates}, {@code Pdp Rates} or
 *       {@code Pricing Zones} with action {@code CREATE}, {@code UPDATE}, {@code DELETE},
 *       {@code ACTIVATE} or {@code DEACTIVATE}, excluding endpoint templates ending in
 *       {@code /calculate} (a price lookup the filter logs as CREATE).</li>
 * </ul>
 * GETs of lists are never logged by the filter, so bulk reading is not measured.
 */
@Component
@RequiredArgsConstructor
public class BulkActivityRule implements ScanRule {

    static final List<String> BULK_ACTIONS = List.of("CANCEL", "EXPORT");
    static final List<String> RATE_MODULES = List.of("Rates", "Customer Rates", "Pdp Rates", "Pricing Zones");
    static final List<String> RATE_EDIT_ACTIONS = List.of("CREATE", "UPDATE", "DELETE", "ACTIVATE", "DEACTIVATE");
    static final String EXCLUDED_ENDPOINT_PATTERN = "%/calculate";

    /** Evidence breaks the total down into at most this many (module, action) pairs. */
    private static final int MAX_BREAKDOWN = 10;

    private final ActivityLogRepository activityLogRepository;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.BULK_ACTIVITY;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        int threshold = properties.getBulkActivity().getThreshold();
        Map<UUID, Tally> byUser = new LinkedHashMap<>();
        Stream.concat(
                activityLogRepository.findSuccessCountsByAction(from, to, BULK_ACTIONS).stream(),
                activityLogRepository.findSuccessCountsByModuleAndAction(
                        from, to, RATE_MODULES, RATE_EDIT_ACTIONS, EXCLUDED_ENDPOINT_PATTERN).stream()
        ).forEach(row -> byUser.computeIfAbsent(row.userId(), k -> new Tally(row.companyId())).add(row));

        return byUser.entrySet().stream()
                .filter(e -> e.getValue().total > threshold)
                .map(e -> {
                    Tally t = e.getValue();
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("actions", t.total);
                    evidence.put("threshold", threshold);
                    evidence.put("byModuleAction", t.breakdown);
                    evidence.put("windowFrom", from.toString());
                    evidence.put("windowTo", to.toString());
                    return new IncidentRequest(t.companyId, e.getKey(), IncidentRule.BULK_ACTIVITY, evidence);
                })
                .toList();
    }

    private static final class Tally {
        final UUID companyId;
        final Map<String, Long> breakdown = new LinkedHashMap<>();
        long total;

        Tally(UUID companyId) {
            this.companyId = companyId;
        }

        void add(UserActionCount row) {
            total += row.count();
            String key = row.module() + " " + row.action();
            if (breakdown.containsKey(key) || breakdown.size() < MAX_BREAKDOWN) {
                breakdown.merge(key, row.count(), Long::sum);
            }
        }
    }
}
