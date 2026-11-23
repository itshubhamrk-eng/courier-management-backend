package com.courier.modules.branchreport.api;

import com.courier.modules.branchreport.api.dto.BranchReportStatusResponse;
import com.courier.modules.branchreport.api.dto.SendOutcomeResponse;
import com.courier.modules.communication.application.BranchReportService;
import com.courier.modules.communication.domain.BranchStatementPort;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Vendor Audit report's monthly branch statement: download the PDF and send it over WhatsApp.
 * The sends reach people outside the company's staff, so unlike the report itself (any
 * authenticated user at the API) these are held to the three roles the report's nav entry allows.
 */
@RestController
@RequestMapping("/api/v1/shipments/vendor-audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Branch Monthly Report", description = "Download or WhatsApp the monthly branch statement")
public class BranchReportController {

    private static final String REPORT_ROLES = "hasAnyRole('COMPANY_ADMIN', 'FINANCE_USER', 'ACCOUNTS')";
    static final long MAX_RANGE_DAYS = 366;

    private final BranchReportService service;
    private final BranchStatementPort statementPort;
    private final AuditService auditService;

    @GetMapping("/{branchId}/pdf")
    @PreAuthorize(REPORT_ROLES)
    @Operation(summary = "Download a branch's statement PDF for the period",
            description = "The same PDF the WhatsApp send attaches. 404 if the branch is not in the "
                    + "caller's company.")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID branchId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validate(from, to);
        BranchStatementPort.GeneratedStatement statement = statementPort
                .generate(CompanyContext.requireCompanyId(), branchId, from, to)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // File names are ASCII slugs ("<branch>-yyyy-MM.pdf"), so the plain quoted form is safe.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + statement.fileName().replace("\"", "") + "\"")
                .body(statement.pdf());
    }

    @PostMapping("/{branchId}/send")
    @PreAuthorize(REPORT_ROLES)
    @Operation(summary = "WhatsApp one branch's statement to its managers",
            description = "Without `force`, a month already sent answers 200 with result "
                    + "`ALREADY_SENT` and sends nothing; call again with `force=true` to resend.")
    public ApiResponse<SendOutcomeResponse> send(@PathVariable UUID branchId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(defaultValue = "false") boolean force) {
        validate(from, to);
        BranchReportService.SendOutcome outcome = service.sendForBranch(branchId, from, to, force);
        audit(branchId, from, to, force, 1);
        return ApiResponse.success(toResponse(outcome));
    }

    @PostMapping("/send-all")
    @PreAuthorize(REPORT_ROLES)
    @Operation(summary = "WhatsApp every branch's statement to its managers",
            description = "One outcome per branch. Already-sent months are skipped unless `force=true`.")
    public ApiResponse<List<SendOutcomeResponse>> sendAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean force) {
        validate(from, to);
        List<SendOutcomeResponse> out = service.sendForAllBranches(from, to, force).stream()
                .map(BranchReportController::toResponse).toList();
        audit(null, from, to, force, out.size());
        return ApiResponse.success(out);
    }

    @GetMapping("/send-status")
    @PreAuthorize(REPORT_ROLES)
    @Operation(summary = "Per-branch WhatsApp send state for the period",
            description = "Manager phone count plus the least-progressed recipient status and the "
                    + "latest send time; null when nothing was attempted.")
    public ApiResponse<List<BranchReportStatusResponse>> sendStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validate(from, to);
        return ApiResponse.success(service.sendStatus(from, to).stream()
                .map(s -> new BranchReportStatusResponse(s.branchId(), s.branchName(), s.managerPhoneCount(),
                        s.lastStatus(), s.lastSentAt()))
                .toList());
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The period cannot be longer than " + MAX_RANGE_DAYS + " days");
        }
    }

    private static SendOutcomeResponse toResponse(BranchReportService.SendOutcome o) {
        return new SendOutcomeResponse(o.branchId(), o.branchName(), o.result());
    }

    private void audit(UUID branchId, LocalDate from, LocalDate to, boolean force, int branches) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.toString());
        details.put("to", to.toString());
        details.put("force", force);
        details.put("allBranches", branchId == null);
        details.put("branches", branches);
        auditService.record(AuditAction.BRANCH_REPORT_SENT, "Branch", branchId, details);
    }
}
