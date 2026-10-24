package com.courier.modules.pdp.api;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.pdp.api.PdpBillingDtos.EarningResponse;
import com.courier.modules.pdp.api.PdpBillingDtos.GenerateRequest;
import com.courier.modules.pdp.api.PdpBillingDtos.PayRequest;
import com.courier.modules.pdp.api.PdpBillingDtos.StatementDetailResponse;
import com.courier.modules.pdp.api.PdpBillingDtos.StatementResponse;
import com.courier.modules.pdp.api.PdpBillingDtos.FrequencyRequest;
import com.courier.modules.pdp.api.PdpBillingDtos.GenerateDueResponse;
import com.courier.modules.pdp.api.PdpBillingDtos.ScheduleResponse;
import com.courier.modules.pdp.application.PdpBillingCycleService;
import com.courier.modules.pdp.application.PdpDashboardService;
import com.courier.modules.pdp.application.PdpEarningService;
import com.courier.modules.pdp.application.PdpStatementService;
import com.courier.modules.pdp.application.PdpStatementService.StatementView;
import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import com.courier.modules.pdp.domain.PdpStatement;
import com.courier.modules.pdp.domain.PdpStatementStatus;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Partner earnings (accrued, never wallet-credited) and billing statements. {@code COMPANY_ADMIN} only (enforced on the services). */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Partner Billing", description = "Earnings and billing statements of Pickup-Delivery Partners")
public class PdpBillingController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PdpEarningService earningService;
    private final PdpStatementService statementService;
    private final BranchService branchService;
    private final PdpBillingCycleService cycleService;
    private final PdpDashboardService dashboardService;

    @GetMapping("/api/v1/pdp-earnings")
    @Operation(summary = "List partner earnings", description = "Newest first. ACCRUED = priced and unbilled, UNRATED = no "
            + "rate card then (amount 0), BILLED = on a statement. `size` capped at 200.")
    public ApiResponse<PageResponse<EarningResponse>> earnings(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) PdpEarningType type,
            @RequestParam(required = false) PdpEarningStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = earningService.search(branchId, type, status, from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        Map<UUID, Branch> cache = new HashMap<>();
        return ApiResponse.success(PageResponse.from(result, e -> toEarning(e, cache)));
    }

    @PostMapping("/api/v1/pdp-earnings/{id}/reprice")
    @Operation(summary = "Re-price an unrated earning", description = "Once a rate card exists for it.")
    public ApiResponse<EarningResponse> reprice(@PathVariable UUID id) {
        return ApiResponse.success(toEarning(earningService.reprice(id), new HashMap<>()), "Earning re-priced");
    }

    @GetMapping("/api/v1/pdp-statements")
    @Operation(summary = "List billing statements")
    public ApiResponse<PageResponse<StatementResponse>> statements(
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) PdpStatementStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = statementService.search(branchId, status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        Map<UUID, Branch> cache = new HashMap<>();
        return ApiResponse.success(PageResponse.from(result, s -> toStatement(s, cache)));
    }

    @GetMapping("/api/v1/pdp-statements/schedule")
    @Operation(summary = "Billing frequency and next statement due, per partner",
            description = "A partner with no setting is billed monthly. `due` = its cycle has closed with unbilled earnings.")
    public ApiResponse<List<ScheduleResponse>> schedule() {
        return ApiResponse.success(cycleService.schedule(PdpBillingCycleService.today()).stream()
                .map(r -> new ScheduleResponse(r.branchId(), r.branchCode(), r.branchName(), r.frequency(), r.lastBilledTo(),
                        r.unbilledEarnings(), r.dueFrom(), r.dueTo(), r.isDue())).toList());
    }

    @PutMapping("/api/v1/pdp-billing-frequency/{branchId}")
    @Operation(summary = "Set a partner's billing frequency", description = "WEEKLY, FIFTEEN_DAYS or MONTHLY.")
    public ApiResponse<FrequencyRequest> setFrequency(@PathVariable UUID branchId, @Valid @RequestBody FrequencyRequest r) {
        return ApiResponse.success(new FrequencyRequest(cycleService.setFrequency(branchId, r.frequency())), "Billing frequency saved");
    }

    @PostMapping("/api/v1/pdp-statements/generate-due")
    @Operation(summary = "Generate all due statements now", description = "Same run the daily job does, for this company.")
    public ApiResponse<GenerateDueResponse> generateDue() {
        int n = cycleService.generateDue(PdpBillingCycleService.today());
        return ApiResponse.success(new GenerateDueResponse(n), n + " statement(s) generated");
    }

    @GetMapping("/api/v1/pdp-statements/{id}")
    @Operation(summary = "A statement with its pickup and delivery lines")
    public ApiResponse<StatementDetailResponse> statement(@PathVariable UUID id) {
        return ApiResponse.success(toDetail(statementService.get(id)));
    }

    @PostMapping("/api/v1/pdp-statements")
    @Operation(summary = "Generate a statement", description = "Bills every ACCRUED, unbilled earning of the partner "
            + "dated within the period. Errors if there are none.")
    public ApiResponse<StatementDetailResponse> generate(@Valid @RequestBody GenerateRequest r) {
        return ApiResponse.success(toDetail(statementService.generate(r.branchId(), r.from(), r.to(), r.remarks())),
                "Statement generated");
    }

    @PatchMapping("/api/v1/pdp-statements/{id}/approve")
    @Operation(summary = "Approve a draft statement")
    public ApiResponse<StatementDetailResponse> approve(@PathVariable UUID id) {
        return ApiResponse.success(toDetail(statementService.approve(id)), "Statement approved");
    }

    @PatchMapping("/api/v1/pdp-statements/{id}/pay")
    @Operation(summary = "Mark an approved statement paid", description = "A manual mark with date and reference; no wallet is touched.")
    public ApiResponse<StatementDetailResponse> pay(@PathVariable UUID id, @Valid @RequestBody PayRequest r) {
        return ApiResponse.success(toDetail(statementService.markPaid(id, r.paidOn(), r.reference())), "Statement marked paid");
    }

    @DeleteMapping("/api/v1/pdp-statements/{id}")
    @Operation(summary = "Delete a draft statement", description = "Releases its earnings back to ACCRUED.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        statementService.delete(id);
        return ApiResponse.success("Statement deleted");
    }

    @GetMapping("/api/v1/pdp-dashboard")
    @Operation(summary = "Pickup-Delivery Partner dashboard", description = "Pickups, deliveries, earnings and statements. "
            + "A company admin sees every partner (or one via `branchId`); a partner login always sees only its own.")
    public ApiResponse<PdpDashboardDtos.DashboardResponse> dashboard(@RequestParam(required = false) UUID branchId) {
        return ApiResponse.success(dashboardService.dashboard(branchId));
    }

    private StatementDetailResponse toDetail(StatementView v) {
        Map<UUID, Branch> cache = new HashMap<>();
        List<EarningResponse> lines = v.lines().stream().map(e -> toEarning(e, cache)).toList();
        return new StatementDetailResponse(toStatement(v.statement(), cache), lines, v.unratedInPeriod());
    }

    private StatementResponse toStatement(PdpStatement s, Map<UUID, Branch> cache) {
        Branch b = branch(s.getBranchId(), cache);
        return new StatementResponse(s.getId(), s.getStatementNo(), s.getBranchId(), b == null ? null : b.getBranchCode(),
                b == null ? null : b.getBranchName(), s.getPeriodFrom(), s.getPeriodTo(), s.getStatus(),
                s.getPickupCount(), s.getDeliveryCount(), s.getPickupTotal(), s.getDeliveryTotal(), s.getNetPayable(),
                s.getApprovedAt(), s.getPaidOn(), s.getPaymentRef(), s.getRemarks(), s.getCreatedAt(), s.getVersion());
    }

    private EarningResponse toEarning(PdpEarning e, Map<UUID, Branch> cache) {
        Branch b = branch(e.getBranchId(), cache);
        return new EarningResponse(e.getId(), e.getBranchId(), b == null ? null : b.getBranchCode(),
                b == null ? null : b.getBranchName(), e.getEarningType(), e.getSourceId(), e.getReferenceNo(),
                e.getEarnedOn(), e.getStatus(), e.getPincode(), e.getDistrictId(), e.getActualWeightKg(),
                e.getChargeableWeightKg(), e.getRatePerKg(), e.getWeightCharge(), e.getMathadiCharge(),
                e.getOdaCharge(), e.getTotalAmount(), e.getRemark(), e.getStatementId());
    }

    private Branch branch(UUID id, Map<UUID, Branch> cache) {
        Branch b = cache.get(id);
        if (b == null) {
            try {
                b = branchService.getById(id);
                cache.put(id, b);
            } catch (ResourceNotFoundException ignored) {
                // partner deleted: rows stay readable, just unnamed
            }
        }
        return b;
    }
}
