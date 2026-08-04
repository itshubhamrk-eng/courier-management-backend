package com.courier.modules.subscription.api;

import com.courier.modules.subscription.api.dto.CreateSubscriptionPlanRequest;
import com.courier.modules.subscription.api.dto.SubscriptionPlanResponse;
import com.courier.modules.subscription.api.dto.SubscriptionPlanSummary;
import com.courier.modules.subscription.api.dto.UpdateSubscriptionPlanRequest;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.PlanType;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.modules.subscription.domain.SubscriptionPlanCriteria;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Subscription plan catalogue — {@code SUPER_ADMIN} only.
 *
 * <p>Thin: validation, query-parameter binding and DTO mapping. Every rule and the
 * authoritative role check live in {@code SubscriptionPlanService}.
 *
 * <p>The plan defines what a company may use. Creating companies against a plan is the
 * next module's job and is not exposed here.
 */
@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subscription Plans", description = "Platform pricing and quota catalogue (SUPER_ADMIN)")
public class SubscriptionPlanController {

    /**
     * Sortable properties, whitelisted.
     *
     * <p>Spring binds {@code ?sort=} straight onto an entity attribute name. An unknown
     * name throws {@code PropertyReferenceException} deep in the repository, which the
     * handler can only render as a 500, and a valid-but-unintended name lets a caller
     * order by columns that are none of their business. The map also hides the
     * {@code isActive} → {@code active} field-name difference from clients.
     */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("planCode", "planCode"),
            Map.entry("planName", "planName"),
            Map.entry("planType", "planType"),
            Map.entry("monthlyPrice", "monthlyPrice"),
            Map.entry("yearlyPrice", "yearlyPrice"),
            Map.entry("currency", "currency"),
            Map.entry("trialDays", "trialDays"),
            Map.entry("displayOrder", "displayOrder"),
            Map.entry("isActive", "active"),
            Map.entry("active", "active"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final SubscriptionPlanService service;
    private final SubscriptionPlanMapper mapper;

    @PostMapping
    @Operation(summary = "Create a plan",
            description = """
                    Plan code and plan name must be unique, including against
                    soft-deleted plans. A `TRIAL` plan must be free and must grant at
                    least one trial day; an `ENTERPRISE` plan has every quota forced to
                    unlimited regardless of what is sent.

                    Omit a quota field, or send null, to mean **unlimited**.
                    """)
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> create(
            @Valid @RequestBody CreateSubscriptionPlanRequest request) {

        SubscriptionPlan plan = service.create(mapper.toCommand(request));
        SubscriptionPlanResponse body = mapper.toResponse(plan);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/subscription-plans/{id}")
                        .buildAndExpand(plan.getId()).toUri())
                .body(ApiResponse.success(body, "Subscription plan created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a plan",
            description = """
                    Full replacement: an omitted quota is written as unlimited.

                    `version` is required and must match the version last read, or the
                    request is rejected with `409 CONCURRENT_MODIFICATION`. `planCode`
                    cannot be changed, and activation has its own endpoints.
                    """)
    public ApiResponse<SubscriptionPlanResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request) {

        SubscriptionPlan plan = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(plan), "Subscription plan updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a plan")
    public ApiResponse<SubscriptionPlanResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List plans",
            description = """
                    Paged, sorted, filtered and searchable.

                    Filters combine with AND; an omitted filter does not constrain.
                    `search` matches plan code, name and description, case-insensitively.
                    Sort accepts `planCode`, `planName`, `planType`, `monthlyPrice`,
                    `yearlyPrice`, `currency`, `trialDays`, `displayOrder`, `isActive`,
                    `createdAt`, `updatedAt`; anything else is rejected with 400.
                    """)
    public ApiResponse<PageResponse<SubscriptionPlanSummary>> list(
            @Parameter(description = "Exact tier match") @RequestParam(required = false) PlanType planType,
            @Parameter(description = "true for the offered catalogue") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "ISO-4217") @RequestParam(required = false) String currency,
            @Parameter(description = "Inclusive lower bound on monthly price") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Inclusive upper bound on monthly price") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Free text over code, name and description") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = {"displayOrder", "planCode"}) Pageable pageable) {

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "minPrice cannot be greater than maxPrice.");
        }

        SubscriptionPlanCriteria criteria = new SubscriptionPlanCriteria(
                planType, isActive, currency, minPrice, maxPrice, search);

        Page<SubscriptionPlan> page = service.search(criteria, sanitise(pageable));

        return ApiResponse.success(PageResponse.from(page, mapper::toSummary));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a plan",
            description = "Makes the plan assignable to new companies. Idempotent.")
    public ApiResponse<SubscriptionPlanResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)),
                "Subscription plan activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a plan",
            description = "Withdraws the plan from the catalogue offered to new companies. "
                    + "Companies already on it are unaffected. Idempotent.")
    public ApiResponse<SubscriptionPlanResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)),
                "Subscription plan deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a plan",
            description = "Soft delete: the row is retained and the plan is deactivated. "
                    + "Its code and name stay reserved, so a later plan cannot reuse them "
                    + "and inherit the old one's history.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        // 200 rather than 204: every response in this API carries the standard envelope,
        // and a 204 must have an empty body.
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Subscription plan deleted"));
    }

    /**
     * Caps the page size and rejects sort properties outside {@link #SORTABLE}.
     *
     * <p>An uncapped {@code size} is a trivial way to turn one request into a full table
     * scan and a multi-megabyte response.
     */
    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(), sortableNames()));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();

        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("planCode"))
                : Sort.by(orders);

        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private static String sortableNames() {
        // "active" is the entity's own field name and is accepted, but "isActive" is the
        // one documented to clients, so only that spelling is advertised.
        Set<String> names = new java.util.TreeSet<>(SORTABLE.keySet());
        names.remove("active");
        return String.join(", ", names);
    }
}
