package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.SlaRuleResponse;
import com.courier.modules.support.api.dto.UpsertSlaRuleRequest;
import com.courier.modules.support.application.TicketSlaRuleService;
import com.courier.modules.support.application.command.UpsertSlaRuleCommand;
import com.courier.modules.support.domain.TicketSlaRule;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** A company's own SLA targets. Reads: any authenticated company user. Writes: COMPANY_ADMIN. */
@RestController
@RequestMapping("/api/v1/support/sla-rules")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ticket SLA Rules", description = "Company-configured first-response/resolution targets per priority")
public class TicketSlaRuleController {

    private final TicketSlaRuleService service;

    private static SlaRuleResponse toResponse(TicketSlaRule r) {
        return new SlaRuleResponse(r.getId(), r.getPriority(), r.getFirstResponseMinutes(),
                r.getResolutionMinutes(), r.isActive(), r.getVersion());
    }

    @GetMapping
    public ApiResponse<List<SlaRuleResponse>> list() {
        return ApiResponse.success(service.list().stream().map(TicketSlaRuleController::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Create or replace the SLA rule for a priority", description = "COMPANY_ADMIN only.")
    public ApiResponse<SlaRuleResponse> upsert(@Valid @RequestBody UpsertSlaRuleRequest request) {
        var saved = service.upsert(new UpsertSlaRuleCommand(
                request.priority(), request.firstResponseMinutes(), request.resolutionMinutes()));
        return ApiResponse.success(toResponse(saved), "SLA rule saved");
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<SlaRuleResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.setActive(id, true)), "SLA rule activated");
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<SlaRuleResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.setActive(id, false)), "SLA rule deactivated");
    }
}
