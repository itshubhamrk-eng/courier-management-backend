package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.AssignSubscriptionRequest;
import com.courier.modules.company.api.dto.CompanyBrandingUploadResponse;
import com.courier.modules.company.api.dto.CompanyResponse;
import com.courier.modules.company.api.dto.CompanySearchRequest;
import com.courier.modules.company.api.dto.CompanyStatisticsResponse;
import com.courier.modules.company.api.dto.CompanySummaryResponse;
import com.courier.modules.company.api.dto.CreateCompanyRequest;
import com.courier.modules.company.api.dto.DeactivateCompanyRequest;
import com.courier.modules.company.api.dto.RenewSubscriptionRequest;
import com.courier.modules.company.api.dto.SuspendCompanyRequest;
import com.courier.modules.company.api.dto.SuspendSubscriptionRequest;
import com.courier.modules.company.api.dto.UpdateCompanyRequest;
import com.courier.modules.company.application.CompanyDashboardService;
import com.courier.modules.company.application.CompanyService;
import com.courier.modules.company.domain.Company;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Companies — the ownership roots of the platform. {@code SUPER_ADMIN} only.
 *
 * <p>Thin: binding, validation and DTO mapping. Every rule, and the authoritative role
 * check, live in {@code CompanyService}.
 *
 * <p>Creating a company also creates its company id, its five default roles with
 * permissions, its default settings and its first administrator — see
 * {@code MEMORY/modules/company.md}. Branches, hubs, customers and shipments are
 * separate modules and are not reachable from here.
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Companies", description = "Company lifecycle and provisioning (SUPER_ADMIN)")
public class CompanyController {

    /**
     * Sortable properties, whitelisted.
     *
     * <p>Spring binds {@code ?sort=} straight onto an entity attribute name: an unknown
     * name throws {@code PropertyReferenceException} deep in the repository and can only
     * be rendered as a 500, while a valid-but-unintended one lets a caller order by
     * fields that are none of their business. The map also hides the
     * {@code isActive} → {@code active} field-name difference from clients.
     */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("companyCode", "companyCode"),
            Map.entry("companyName", "companyName"),
            Map.entry("legalName", "legalName"),
            Map.entry("status", "status"),
            Map.entry("isActive", "active"),
            Map.entry("active", "active"),
            Map.entry("email", "email"),
            Map.entry("city", "city"),
            Map.entry("state", "state"),
            Map.entry("country", "country"),
            Map.entry("trialEndDate", "trialEndDate"),
            Map.entry("subscriptionEndDate", "subscriptionEndDate"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyService service;
    private final CompanyDashboardService dashboardService;
    private final CompanyMapper mapper;

    @PostMapping
    @Operation(summary = "Create a company",
            description = """
                    Creates the company and everything it needs to operate, in one
                    transaction:

                    * a generated `companyId`, stamped on every row the company will own
                    * the subscription plan link (the plan must exist and be active)
                    * the default roles with permissions, filtered by the plan's features
                    * default settings, including the plan's quotas as read-only rows
                    * the first Company Admin, holding `COMPANY_ADMIN`, created `PENDING`
                      with a **temporary password** and an activation email

                    `provisioning.temporaryPassword` is readable **here and nowhere
                    else, ever** — it is not logged, not audited, not emailed and not
                    retrievable from any later request. Show it once to the operator who
                    made the call and say so; a lost one is reset, not recovered.

                    The account is `PENDING`, so the password alone opens nothing until
                    the activation link is followed — which is what makes returning it
                    acceptable. Check `provisioning.activationEmailSent`: when false the
                    account exists but the link must be reissued.
                    """)
    public ResponseEntity<ApiResponse<CompanyResponse>> create(
            @Valid @RequestBody CreateCompanyRequest request) {

        CompanyService.CreatedCompany created = service.create(mapper.toCommand(request));
        CompanyResponse body = mapper.toCreatedResponse(created);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/companies/{id}")
                        .buildAndExpand(created.company().getId()).toUri())
                .body(ApiResponse.success(body, "Company created"));
    }

    @PostMapping(value = "/branding-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a company logo or favicon",
            description = "PNG/JPEG/SVG/WEBP/ICO only. Not tied to a company id — the create "
                    + "form has none yet — so this only stores the file and returns its URL; "
                    + "the caller writes that URL into the company record itself via create "
                    + "or update.")
    public ApiResponse<CompanyBrandingUploadResponse> uploadBranding(
            @RequestParam("kind") String kind, @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
        String url = service.uploadBranding(kind, content, file.getOriginalFilename(), file.getContentType());
        return ApiResponse.success(new CompanyBrandingUploadResponse(url), "File uploaded");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a company",
            description = """
                    Full replacement: an omitted optional field is written as null.

                    `version` is required and must match the version last read, or the
                    request is rejected with `409 CONCURRENT_MODIFICATION`. `companyCode`
                    and `companyId` are immutable, and status changes have their own
                    endpoints so each transition is validated and audited separately.
                    """)
    public ApiResponse<CompanyResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateCompanyRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.update(id, mapper.toCommand(request))), "Company updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a company")
    public ApiResponse<CompanyResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List companies",
            description = """
                    Paged, sorted, filtered and searchable. Filters combine with AND;
                    an omitted filter does not constrain.

                    `expiringBefore` is the renewals worklist: it matches a trial *or* a
                    subscription ending on or before that date. `search` covers code,
                    name, legal name, email and mobile, case-insensitively.

                    Sort accepts `companyCode`, `companyName`, `legalName`, `status`,
                    `isActive`, `email`, `city`, `state`, `country`, `trialEndDate`,
                    `subscriptionEndDate`, `createdDate`, `updatedDate`; anything else is
                    rejected with 400. `size` is capped at 100.
                    """)
    public ApiResponse<PageResponse<CompanySummaryResponse>> list(
            @Valid @ParameterObject CompanySearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        if (search != null && search.createdFrom() != null && search.createdTo() != null
                && search.createdFrom().isAfter(search.createdTo())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "createdFrom cannot be after createdTo.");
        }

        Page<Company> page = service.search(mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toSummary));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a company",
            description = "Moves TRIAL, INACTIVE, SUSPENDED or EXPIRED to ACTIVE. Idempotent.")
    public ApiResponse<CompanyResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Company activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a company",
            description = """
                    Switches the company off. Legal from every status except INACTIVE,
                    and idempotent.

                    Deliberately distinct from suspend: a deactivated company is
                    dormant, a suspended one is in trouble, and support quotes the
                    difference back to the customer. Both stop authentication.

                    The reason is optional — demanding one for routine housekeeping
                    only teaches operators to type "n/a".
                    """)
    public ApiResponse<CompanyResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DeactivateCompanyRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(
                mapper.toResponse(service.deactivate(id, reason)), "Company deactivated");
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend a company",
            description = """
                    Blocks the company: its users can no longer authenticate. Legal from
                    TRIAL and ACTIVE only. Idempotent.

                    A reason is required — it goes to the audit trail, the suspension
                    event and the company's remarks. Existing access tokens keep working
                    until they expire, at most 15 minutes.
                    """)
    public ApiResponse<CompanyResponse> suspend(@PathVariable UUID id,
                                                @Valid @RequestBody SuspendCompanyRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.suspend(id, request.reason())), "Company suspended");
    }

    @PatchMapping("/{id}/expire")
    @Operation(summary = "Expire a company",
            description = "Ends the trial or subscription window. Legal from TRIAL and "
                    + "ACTIVE only. Reversible with activate. Idempotent.")
    public ApiResponse<CompanyResponse> expire(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.expire(id)), "Company expired");
    }

    @PostMapping("/{id}/subscription")
    @Operation(summary = "Assign a subscription",
            description = """
                    Moves the company onto a plan, opens a paid window and activates it.

                    Separate from `PUT /companies/{id}`, which can also change the plan
                    id: that is an edit of the company record, this is the commercial
                    act. Keeping them apart is what makes "when did Acme move up to
                    ENTERPRISE, and who approved it" answerable from the audit trail.

                    Supply either a `billingCycle` (the end date is derived) or an
                    explicit `endDate` for a negotiated term. Assigning a paid plan
                    closes any trial window — two open windows and no rule about which
                    one is in force is not a state worth having.
                    """)
    public ApiResponse<CompanyResponse> assignSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody AssignSubscriptionRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.assignSubscription(id, mapper.toCommand(request))),
                "Subscription assigned");
    }

    @PostMapping("/{id}/subscription/renew")
    @Operation(summary = "Renew a subscription",
            description = """
                    Extends the paid window from the later of its current end and today.

                    Paying early therefore keeps the days already bought; paying late
                    does not bill for the lapsed gap. There is no start date in the
                    request because it is not the caller's to choose.

                    A renewal reactivates an EXPIRED or SUSPENDED company — that is the
                    point of the operation, so it does not need a second call. Pass
                    `subscriptionPlanId` to upgrade or downgrade with the new period.
                    """)
    public ApiResponse<CompanyResponse> renewSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody RenewSubscriptionRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.renewSubscription(id, mapper.toCommand(request))),
                "Subscription renewed");
    }

    @PostMapping("/{id}/subscription/suspend")
    @Operation(summary = "Suspend a subscription",
            description = """
                    Stops the subscription: the company is suspended and the paid window
                    is closed as of today, so it stops appearing on renewals reports as
                    paid until some future date. Reversible by renewing.

                    A reason is required — it goes to the audit trail, the event and the
                    company's remarks.
                    """)
    public ApiResponse<CompanyResponse> suspendSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody SuspendSubscriptionRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.suspendSubscription(id, request.reason())),
                "Subscription suspended");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a company",
            description = """
                    Soft delete: the row is retained and the company is deactivated, so it
                    disappears from every read and serves no requests.

                    Its users, roles and settings are deliberately left in place — they
                    carry the company id, and cascading a delete across every company-owned
                    table is not reversible. The company code, email and tax numbers stay
                    reserved.
                    """)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        // 200 rather than 204: every response carries the standard envelope, and a 204
        // must have an empty body.
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Company deleted"));
    }

    @GetMapping("/{id}/statistics")
    @Operation(summary = "Company statistics",
            description = """
                    Counts and subscription position for one company: users (total,
                    active, pending), branches (total, active), roles, the plan's
                    ceilings, and whether either ceiling has been reached.

                    `daysToExpiry` counts down to whichever of the trial and
                    subscription ends sooner — the one that actually stops the company
                    working. It goes negative once lapsed, and is null when the company
                    has neither date, which is a data problem worth seeing rather than
                    hiding behind a zero.

                    **There is no `shipmentCount`.** The shipments module does not exist
                    yet, and a field that is always zero reads as "this company has
                    booked nothing" rather than "nobody has built this" — the two are
                    indistinguishable on screen. It arrives with the module that can
                    populate it.
                    """)
    public ApiResponse<CompanyStatisticsResponse> statistics(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(dashboardService.statisticsFor(id)));
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "List a company's roles",
            description = "The eight seeded roles as a super admin sees them for one "
                    + "company. Grants are not included — read them per role from "
                    + "`/api/v1/roles/{roleId}/permissions`. The company's own admins "
                    + "manage roles through `/api/v1/roles`.")
    public ApiResponse<List<CompanyRoleResponse>> roles(@PathVariable UUID id) {
        return ApiResponse.success(service.listRoles(id).stream()
                .map(role -> new CompanyRoleResponse(
                        role.getId(),
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getDescription(),
                        role.getRoleType().name(),
                        role.isSystemRole(),
                        role.isDefaultRole(),
                        role.getStatus().name()))
                .toList());
    }

    @GetMapping("/{id}/settings")
    @Operation(summary = "List a company's settings",
            description = "Seeded configuration, ordered by category. Rows marked "
                    + "`planDerived` come from the subscription plan; an empty value on a "
                    + "`limit.*` key means unlimited.")
    public ApiResponse<List<CompanySettingResponse>> settings(@PathVariable UUID id) {
        return ApiResponse.success(service.listSettings(id).stream()
                .map(setting -> new CompanySettingResponse(
                        setting.getSettingKey(),
                        setting.getSettingValue(),
                        setting.getCategory(),
                        setting.isPlanDerived(),
                        setting.getDescription()))
                .toList());
    }

    /** Projection of a seeded role. Declared here because only this controller returns it. */
    public record CompanyRoleResponse(UUID id,
                                      String roleCode,
                                      String roleName,
                                      String description,
                                      String roleType,
                                      boolean systemRole,
                                      boolean isDefault,
                                      String status) {
    }

    /** Projection of a seeded setting. */
    public record CompanySettingResponse(String settingKey,
                                         String settingValue,
                                         String category,
                                         boolean planDerived,
                                         String description) {
    }

    /**
     * Caps the page size and rejects sort properties outside {@link #SORTABLE}. An
     * uncapped {@code size} turns one request into a full table scan.
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

        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.desc("createdAt")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private static String sortableNames() {
        // "active" and "createdAt" are the entity's own field names and are accepted,
        // but only the documented client spellings are advertised.
        Set<String> names = new TreeSet<>(SORTABLE.keySet());
        names.remove("active");
        names.remove("createdAt");
        return String.join(", ", names);
    }
}
