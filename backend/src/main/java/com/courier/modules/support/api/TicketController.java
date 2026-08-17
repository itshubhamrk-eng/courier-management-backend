package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.AssignmentRequest;
import com.courier.modules.support.api.dto.ChangeCategoryRequest;
import com.courier.modules.support.api.dto.ChangePriorityRequest;
import com.courier.modules.support.api.dto.ChangeStatusRequest;
import com.courier.modules.support.api.dto.CreateTicketRequest;
import com.courier.modules.support.api.dto.RemarksRequest;
import com.courier.modules.support.api.dto.ReplyRequest;
import com.courier.modules.support.api.dto.TicketAttachmentResponse;
import com.courier.modules.support.api.dto.TicketDetailResponse;
import com.courier.modules.support.api.dto.TicketMessageResponse;
import com.courier.modules.support.api.dto.TicketResponse;
import com.courier.modules.support.api.dto.TicketSearchRequest;
import com.courier.modules.support.application.TicketDashboardStats;
import com.courier.modules.support.application.TicketService;
import com.courier.modules.support.application.command.AssignmentCommand;
import com.courier.modules.support.application.command.ReplyCommand;
import com.courier.modules.support.application.command.UploadTicketAttachmentCommand;
import com.courier.modules.support.domain.Ticket;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * Ticket Support, Phase 1: create -> assign -> in progress -> communication -> resolution
 * -> closed, with reopen. See {@link TicketService} for the full scoping/authorisation
 * story — SLA rules and notifications are Phase 2, not here.
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ticket Support", description = "Support tickets: lifecycle, conversation, assignment")
public class TicketController {

    private final TicketService service;
    private final TicketMapper mapper;

    @PostMapping
    @Operation(summary = "Raise a ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> create(@Valid @RequestBody CreateTicketRequest request) {
        Ticket created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/support/tickets/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Ticket raised"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ticket", description = "Includes the conversation, attachments and "
            + "status/assignment timeline. Internal notes are omitted for a non-staff caller.")
    public ApiResponse<TicketDetailResponse> get(@PathVariable UUID id) {
        Ticket ticket = service.getById(id);
        return ApiResponse.success(mapper.toDetail(ticket, service.messages(id), service.attachments(id),
                service.statusHistory(id), service.assignmentHistory(id)));
    }

    @GetMapping
    @Operation(summary = "Search tickets", description = "A non-admin caller sees only tickets they raised, "
            + "are assigned to, or that belong to their own branch. SUPER_ADMIN sees every tenant unless "
            + "`companyId` narrows to one.")
    public ApiResponse<PageResponse<TicketResponse>> list(
            @ParameterObject TicketSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<Ticket> page = service.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Ticket dashboard stats", description = "SUPER_ADMIN sees every tenant "
            + "unless `companyId` narrows to one.")
    public ApiResponse<TicketDashboardStats> dashboard(@RequestParam(required = false) UUID companyId) {
        return ApiResponse.success(service.dashboard(companyId));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Reply", description = "The requester or staff on this ticket. "
            + "`internalNote=true` is refused for a non-staff caller.")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> reply(
            @PathVariable UUID id, @Valid @RequestBody ReplyRequest request) {
        var message = service.reply(id, new ReplyCommand(request.body(), request.internalNote()));
        return ResponseEntity.status(201).body(ApiResponse.success(mapper.toResponse(message), "Reply added"));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an attachment", description = "JPEG/PNG/WEBP/HEIC image, PDF, or Office "
            + "document, up to 10 MB. Omit `messageId` to attach at ticket level.")
    public ResponseEntity<ApiResponse<TicketAttachmentResponse>> uploadAttachment(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID messageId) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
        var attachment = service.uploadAttachment(id, new UploadTicketAttachmentCommand(
                content, file.getOriginalFilename(), file.getContentType(), messageId));
        return ResponseEntity.status(201).body(ApiResponse.success(mapper.toResponse(attachment), "File uploaded"));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign", description = "COMPANY_ADMIN/SUPER_ADMIN. Only from OPEN.")
    public ApiResponse<TicketResponse> assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        Ticket ticket = service.assign(id, new AssignmentCommand(request.assigneeUserId(), request.remarks()));
        return ApiResponse.success(mapper.toResponse(ticket), "Ticket assigned");
    }

    @PatchMapping("/{id}/reassign")
    @Operation(summary = "Reassign", description = "COMPANY_ADMIN/SUPER_ADMIN. Any non-terminal status.")
    public ApiResponse<TicketResponse> reassign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        Ticket ticket = service.reassign(id, new AssignmentCommand(request.assigneeUserId(), request.remarks()));
        return ApiResponse.success(mapper.toResponse(ticket), "Ticket reassigned");
    }

    @PatchMapping("/{id}/escalate")
    @Operation(summary = "Escalate", description = "COMPANY_ADMIN/SUPER_ADMIN. Marks the ticket "
            + "escalated; optionally reassigns in the same call.")
    public ApiResponse<TicketResponse> escalate(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        Ticket ticket = service.escalate(id, new AssignmentCommand(request.assigneeUserId(), request.remarks()));
        return ApiResponse.success(mapper.toResponse(ticket), "Ticket escalated");
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change status", description = "The assigned agent or COMPANY_ADMIN/SUPER_ADMIN. "
            + "Refused if not a legal transition; use reopen/close for those two.")
    public ApiResponse<TicketResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        Ticket ticket = service.changeStatus(id, request.status(), request.remarks());
        return ApiResponse.success(mapper.toResponse(ticket), "Status updated");
    }

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Change priority")
    public ApiResponse<TicketResponse> changePriority(@PathVariable UUID id, @Valid @RequestBody ChangePriorityRequest request) {
        Ticket ticket = service.changePriority(id, request.priority(), request.remarks());
        return ApiResponse.success(mapper.toResponse(ticket), "Priority updated");
    }

    @PatchMapping("/{id}/category")
    @Operation(summary = "Change category/sub-category")
    public ApiResponse<TicketResponse> changeCategory(@PathVariable UUID id, @Valid @RequestBody ChangeCategoryRequest request) {
        Ticket ticket = service.changeCategory(id, request.categoryId(), request.subCategoryId(), request.remarks());
        return ApiResponse.success(mapper.toResponse(ticket), "Category updated");
    }

    @PatchMapping("/{id}/reopen")
    @Operation(summary = "Reopen", description = "The requester or COMPANY_ADMIN/SUPER_ADMIN. "
            + "Only from RESOLVED/CLOSED.")
    public ApiResponse<TicketResponse> reopen(@PathVariable UUID id, @RequestBody(required = false) RemarksRequest request) {
        Ticket ticket = service.reopen(id, request == null ? null : request.remarks());
        return ApiResponse.success(mapper.toResponse(ticket), "Ticket reopened");
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Close", description = "The assigned agent or COMPANY_ADMIN/SUPER_ADMIN. Only from RESOLVED.")
    public ApiResponse<TicketResponse> close(@PathVariable UUID id, @RequestBody(required = false) RemarksRequest request) {
        Ticket ticket = service.close(id, request == null ? null : request.remarks());
        return ApiResponse.success(mapper.toResponse(ticket), "Ticket closed");
    }
}
