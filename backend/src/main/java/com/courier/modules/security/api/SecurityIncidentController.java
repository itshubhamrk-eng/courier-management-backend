package com.courier.modules.security.api;

import com.courier.modules.security.api.dto.IncidentActionRequest;
import com.courier.modules.security.api.dto.IncidentResponse;
import com.courier.modules.security.application.IncidentFilter;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Security incidents and the ban / dismiss / unban decision. Gated on the
 * {@code SECURITY_INCIDENT_READ} / {@code SECURITY_INCIDENT_UPDATE} permission codes (so a
 * menu-granted permission works, not only a role) plus PLATFORM_ADMIN, who works the
 * platform-level (company-less) incidents. Company isolation lives in
 * {@code SecurityIncidentService}. The acting user is always the authenticated principal.
 */
@RestController
@RequestMapping("/api/v1/security/incidents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Security Incidents", description = "Detected account-compromise incidents and ban decisions")
public class SecurityIncidentController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final String READERS =
            "hasRole('" + Roles.PLATFORM_ADMIN + "') or hasAuthority('SECURITY_INCIDENT_READ')";
    private static final String UPDATERS =
            "hasRole('" + Roles.PLATFORM_ADMIN + "') or hasAuthority('SECURITY_INCIDENT_UPDATE')";

    private final SecurityIncidentService service;
    private final IncidentResponseAssembler assembler;

    @GetMapping
    @PreAuthorize(READERS)
    @Operation(summary = "List security incidents",
            description = "Newest first. Page size defaults to 20 and is capped at 100. "
                    + "An unknown status/severity/rule value is a 400.")
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) IncidentRule rule,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "detectedAt"));
        Page<SecurityIncident> result = service.list(
                new IncidentFilter(status, severity, rule, userId, null, null), pageable);
        return ApiResponse.success(PageResponse.from(
                new org.springframework.data.domain.PageImpl<>(
                        assembler.toResponses(result.getContent()), pageable, result.getTotalElements())));
    }

    @GetMapping("/summary")
    @PreAuthorize(READERS)
    @Operation(summary = "Incident counts by status",
            description = "Keys OPEN, BANNED, DISMISSED, UNBANNED, within the caller's visibility.")
    public ApiResponse<Map<IncidentStatus, Long>> summary() {
        return ApiResponse.success(service.countByStatus());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READERS)
    @Operation(summary = "One incident with user, roles, branch and parsed evidence")
    public ApiResponse<IncidentResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(assembler.toResponse(service.get(id)));
    }

    @PostMapping("/{id}/ban")
    @PreAuthorize(UPDATERS)
    @Operation(summary = "Ban the incident's user",
            description = "Banning a SUPER_ADMIN / PLATFORM_ADMIN account needs a PLATFORM_ADMIN caller.")
    public ApiResponse<IncidentResponse> ban(@PathVariable UUID id,
                                             @Valid @RequestBody(required = false) IncidentActionRequest body) {
        return ApiResponse.success(assembler.toResponse(service.ban(id, actor(), note(body))));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize(UPDATERS)
    @Operation(summary = "Dismiss the incident as a false positive")
    public ApiResponse<IncidentResponse> dismiss(@PathVariable UUID id,
                                                 @Valid @RequestBody(required = false) IncidentActionRequest body) {
        return ApiResponse.success(assembler.toResponse(service.dismiss(id, actor(), note(body))));
    }

    @PostMapping("/{id}/unban")
    @PreAuthorize(UPDATERS)
    @Operation(summary = "Lift the ban raised by a BANNED incident")
    public ApiResponse<IncidentResponse> unban(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) IncidentActionRequest body) {
        return ApiResponse.success(assembler.toResponse(service.unban(id, actor(), note(body))));
    }

    /** The acting user is whoever is authenticated; nothing in the request can change that. */
    private UUID actor() {
        return SecurityUtils.requireCurrentUser().userId();
    }

    private String note(IncidentActionRequest body) {
        return body == null ? null : body.note();
    }
}
