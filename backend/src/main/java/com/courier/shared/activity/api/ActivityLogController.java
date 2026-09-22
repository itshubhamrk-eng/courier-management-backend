package com.courier.shared.activity.api;

import com.courier.shared.activity.api.dto.ActivityLogResponse;
import com.courier.shared.activity.api.dto.ActivityLogSearchRequest;
import com.courier.shared.activity.application.ActivityLogService;
import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The Admin Activity Log screen: search/filter/paginate, one entry's full detail, and a
 * bounded CSV export. Gated on the existing {@code AUDIT_READ}/{@code AUDIT_SEARCH}/
 * {@code AUDIT_EXPORT} permission codes (seeded since {@code V6}, unused until this
 * module) — see {@code ActivityLogService}'s own note on why no
 * {@code ACTIVITY_LOG_VIEW}/{@code ACTIVITY_LOG_EXPORT} codes were added.
 *
 * <p>The User Activity screen (one user's login history + recent activity + active
 * sessions) is a separate endpoint in {@code modules/auth} — it composes
 * {@code LoginHistory}/{@code UserSession} with this module's data, and {@code shared}
 * must never import from {@code modules} (AI_CONTEXT.md decision 12).
 */
@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Activity Log", description = "Automatic, company-isolated trail of every user action")
public class ActivityLogController {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final ActivityLogService service;
    private final ActivityLogMapper mapper;

    @GetMapping
    @Operation(summary = "Search the activity log",
            description = "Filtered by user/module/action/entity/status/date range/free text. "
                    + "Company-isolated: a non-platform caller's own company is always the scope, "
                    + "regardless of any company filter the caller might try to pass.")
    public ApiResponse<PageResponse<ActivityLogResponse>> search(
            @ParameterObject ActivityLogSearchRequest search,
            @ParameterObject @PageableDefault(size = 25, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<ActivityLog> page = service.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One activity log entry in full",
            description = "Complete detail: old/new value, request info, user info, timestamp.")
    public ApiResponse<ActivityLogResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export the filtered activity log as CSV",
            description = "Same filters as the search endpoint, newest-first, capped at "
                    + MAX_EXPORT_ROWS + " rows so one export cannot pull the whole table.")
    public ResponseEntity<byte[]> export(@ParameterObject ActivityLogSearchRequest search) {
        List<ActivityLog> rows = service.export(mapper.toCriteria(search), MAX_EXPORT_ROWS);
        byte[] csv = toCsv(rows);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-log.csv\"")
                .body(csv);
    }

    private byte[] toCsv(List<ActivityLog> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8)) {
            writer.println("Timestamp,User,Module,Submodule,Action,Entity Type,Entity Id,"
                    + "Description,Status,IP Address,Device,Browser,OS,Error Message");
            for (ActivityLog a : rows) {
                writer.println(String.join(",",
                        csvField(String.valueOf(a.getOccurredAt())), csvField(a.getUsername()),
                        csvField(a.getModule()), csvField(a.getSubmodule()), csvField(a.getAction()),
                        csvField(a.getEntityType()), csvField(a.getEntityId()), csvField(a.getDescription()),
                        csvField(String.valueOf(a.getStatus())), csvField(a.getIpAddress()),
                        csvField(a.getDevice()), csvField(a.getBrowser()), csvField(a.getOs()),
                        csvField(a.getErrorMessage())));
            }
        }
        return out.toByteArray();
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
