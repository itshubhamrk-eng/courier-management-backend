package com.courier.modules.followup.api;

import com.courier.modules.followup.api.dto.AddFollowUpNoteRequest;
import com.courier.modules.followup.api.dto.AssignFollowUpRequest;
import com.courier.modules.followup.api.dto.ChangeFollowUpStatusRequest;
import com.courier.modules.followup.api.dto.CreateFollowUpRequest;
import com.courier.modules.followup.api.dto.FollowUpHistoryResponse;
import com.courier.modules.followup.api.dto.FollowUpResponse;
import com.courier.modules.followup.api.dto.FollowUpSearchRequest;
import com.courier.modules.followup.api.dto.RescheduleRequest;
import com.courier.modules.followup.api.dto.UpdateFollowUpRequest;
import com.courier.modules.followup.application.FollowUpDashboardStats;
import com.courier.modules.followup.application.FollowUpService;
import com.courier.modules.followup.application.command.RescheduleCommand;
import com.courier.modules.followup.domain.FollowUp;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Follow-up Management: branch users track operational tasks (customer/shipment/
 * delivery/payment/exception/general) that need manual action. See {@link
 * FollowUpService} for the full scoping/authorisation story — company- and
 * branch-isolated, no SUPER_ADMIN cross-tenant view (unlike Ticket Support).
 */
@RestController
@RequestMapping("/api/v1/follow-ups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Follow-up Management", description = "Branch operational follow-ups: create, assign, reschedule, complete")
public class FollowUpController {

    private final FollowUpService service;
    private final FollowUpMapper mapper;

    @PostMapping
    @Operation(summary = "Create a follow-up")
    public ResponseEntity<ApiResponse<FollowUpResponse>> create(@Valid @RequestBody CreateFollowUpRequest request) {
        FollowUp created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/follow-ups/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Follow-up created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a follow-up", description = "Full replacement. Refused once COMPLETED/CANCELLED.")
    public ApiResponse<FollowUpResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateFollowUpRequest request) {
        FollowUp updated = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(updated), "Follow-up updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a follow-up")
    public ApiResponse<FollowUpResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Search follow-ups", description = "A branch (non-admin) caller sees only their own "
            + "branch's follow-ups, plus any they created or are assigned. COMPANY_ADMIN sees the whole company.")
    public ApiResponse<PageResponse<FollowUpResponse>> list(
            @ParameterObject FollowUpSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC)
            Pageable pageable) {
        Page<FollowUp> page = service.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Follow-up dashboard counts", description = "Overdue / Due Today / Upcoming / Urgent, "
            + "for the caller's company — backs the Operations Dashboard's Follow-up widget.")
    public ApiResponse<FollowUpDashboardStats> dashboard() {
        return ApiResponse.success(service.dashboard());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change status", description = "The assignee, the creator, this branch's manager, or "
            + "COMPANY_ADMIN. Every change writes a history entry; RESCHEDULED is refused here (use the "
            + "dedicated reschedule action).")
    public ApiResponse<FollowUpResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeFollowUpStatusRequest request) {
        FollowUp updated = service.changeStatus(id, request.status(), request.remarks());
        return ApiResponse.success(mapper.toResponse(updated), "Status updated");
    }

    @PostMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule", description = "Moves the due date and sets status to RESCHEDULED. "
            + "Refused once COMPLETED/CANCELLED. Always writes a history entry.")
    public ApiResponse<FollowUpResponse> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        FollowUp updated = service.reschedule(id, new RescheduleCommand(request.newDueDate(), request.reason()));
        return ApiResponse.success(mapper.toResponse(updated), "Follow-up rescheduled");
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign", description = "COMPANY_ADMIN/BRANCH_MANAGER/HUB_MANAGER. The assignee must "
            + "belong to (or manage) this follow-up's branch.")
    public ApiResponse<FollowUpResponse> assign(@PathVariable UUID id, @Valid @RequestBody AssignFollowUpRequest request) {
        FollowUp updated = service.assign(id, request.assignedUserId(), request.remarks());
        return ApiResponse.success(mapper.toResponse(updated), "Follow-up assigned");
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add a note", description = "Appends to the follow-up's history without changing its status.")
    public ResponseEntity<ApiResponse<FollowUpHistoryResponse>> addNote(@PathVariable UUID id, @Valid @RequestBody AddFollowUpNoteRequest request) {
        var entry = service.addNote(id, request.note());
        return ResponseEntity.status(201).body(ApiResponse.success(mapper.toResponse(entry), "Note added"));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "History", description = "Every creation/status-change/reschedule/assignment/note entry, oldest first.")
    public ApiResponse<List<FollowUpHistoryResponse>> history(@PathVariable UUID id) {
        return ApiResponse.success(service.history(id).stream().map(mapper::toResponse).toList());
    }
}
