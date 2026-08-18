package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CreditRequest;
import com.courier.modules.finance.api.dto.DebitRequest;
import com.courier.modules.finance.api.dto.RechargeOrderResponse;
import com.courier.modules.finance.api.dto.RechargeRequest;
import com.courier.modules.finance.api.dto.WalletResponse;
import com.courier.modules.finance.api.dto.WalletSummaryResponse;
import com.courier.modules.finance.api.dto.WalletTransactionResponse;
import com.courier.modules.finance.api.dto.WalletTransactionSearchRequest;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletTransaction;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The branch wallet.
 *
 * <p>Singular in the path on purpose: a caller has one wallet — their branch's. There is no
 * {@code /branch-wallets/{id}} collection to walk, and no wallet id in any URL, so a wallet
 * cannot be reached by guessing an identifier. A company admin selects a branch with a
 * {@code branchId} parameter and is authorised for it; anyone else gets their own branch and
 * a 404 for anybody else's.
 *
 * <p>Enforced on {@code WalletService}: reads for any authenticated company user within
 * their scope; recharge for the branch (and an admin acting for it); credit and debit for
 * {@code COMPANY_ADMIN} only.
 *
 * <p>There is no endpoint that writes a balance, and there never should be — every figure
 * moves through a transaction that is recorded.
 */
@RestController
@RequestMapping("/api/v1/branch-wallet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Branch Wallet", description = "Prepaid wallet of a branch: balance, statement, money in and out")
public class BranchWalletController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("transactionNo", "transactionNo"),
            Map.entry("amount", "amount"),
            Map.entry("balanceAfter", "balanceAfter"),
            Map.entry("transactionType", "transactionType"),
            Map.entry("subTransactionType", "subTransactionType"),
            Map.entry("paymentStatus", "paymentStatus"));

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletService service;
    private final WalletMapper mapper;

    @GetMapping
    @Operation(summary = "Get the wallet",
            description = "Your branch's wallet. A `COMPANY_ADMIN` may pass `branchId` to "
                    + "read another branch's; anyone else asking for one gets 404. The wallet "
                    + "is created on first access if the branch predates this module.")
    public ApiResponse<WalletResponse> get(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID branchId) {
        return ApiResponse.success(mapper.toResponse(service.getForBranch(branchId)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Wallet summary",
            description = "Balances plus today's, this month's and lifetime credit and debit, "
                    + "the ledger size, and the last recharge. Every figure is derived "
                    + "server-side from settled entries only — a payment that has not been "
                    + "confirmed is not counted as money.")
    public ApiResponse<WalletSummaryResponse> summary(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID branchId) {
        return ApiResponse.success(mapper.toResponse(service.summarise(branchId)));
    }

    @GetMapping("/company-summary")
    @Operation(summary = "Wallet summary for every branch",
            description = "`COMPANY_ADMIN`/`FINANCE_USER` only — one `summary` row per "
                    + "active branch of the caller's company, the same figures "
                    + "`GET /branch-wallet/summary` returns for one branch. Powers the "
                    + "Finance Report's company-wide table.")
    public ApiResponse<List<WalletSummaryResponse>> companySummary() {
        return ApiResponse.success(service.companySummary().stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transaction history",
            description = """
                    The statement: paged, sorted, filtered, searchable, and flat enough to
                    export as-is. Filter by direction, reason, reference kind, payment status,
                    date range, amount range or free text. Sort: `createdDate`,
                    `transactionNo`, `amount`, `balanceAfter`, `transactionType`,
                    `subTransactionType`, `paymentStatus`. Newest first by default; `size`
                    capped at 100, so an export walks the pages.
                    """)
    public ApiResponse<PageResponse<WalletTransactionResponse>> transactions(
            @Valid @ParameterObject WalletTransactionSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        UUID branchId = search == null ? null : search.branchId();
        Page<WalletTransaction> page =
                service.searchTransactions(branchId, mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @PostMapping("/recharge/order")
    @Operation(summary = "Open a recharge payment",
            description = """
                    Step one of a Razorpay recharge. The server fixes the amount at the
                    gateway and returns the order for checkout — this is what makes the flow
                    securable, because the amount is decided before the browser is involved.
                    Nothing is credited here. Requires a configured gateway; without one this
                    returns 422 and manual credit is the alternative.
                    """)
    public ApiResponse<RechargeOrderResponse> openRecharge(
            @Valid @RequestBody RechargeRequest request) {
        if (request.amount() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "'amount' is required to open a recharge.");
        }
        PaymentGatewayPort.GatewayOrder order = service.openRecharge(mapper.toCommand(request));
        Wallet wallet = service.getForBranch(request.branchId());
        return ApiResponse.success(
                mapper.toResponse(order, wallet.getId(), wallet.getWalletNumber(), wallet.getBranchId()),
                "Recharge order created");
    }

    @PostMapping("/recharge")
    @Operation(summary = "Recharge the wallet",
            description = """
                    Step two: settles the payment checkout produced. The signature is verified
                    and the gateway is then asked what was actually paid — **that** figure is
                    credited, not the one in the request body. Idempotent on
                    `paymentReference`: retries, double submits and a repeated webhook all
                    credit the wallet exactly once.
                    """)
    public ApiResponse<WalletTransactionResponse> recharge(
            @Valid @RequestBody RechargeRequest request) {
        WalletTransaction entry = service.completeRecharge(mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(entry), "Wallet recharged");
    }

    @PostMapping("/credit")
    @Operation(summary = "Credit a wallet",
            description = "`COMPANY_ADMIN` only. Writes a CR entry and raises the balance in "
                    + "one transaction. The reason must be one that can appear on a credit.")
    public ApiResponse<WalletTransactionResponse> credit(@Valid @RequestBody CreditRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.credit(mapper.toCommand(request))), "Wallet credited");
    }

    @PostMapping("/debit")
    @Operation(summary = "Debit a wallet",
            description = "`COMPANY_ADMIN` only. Writes a DR entry and lowers the balance in "
                    + "one transaction. A wallet is prepaid: a debit beyond the available "
                    + "balance is refused with 422 rather than overdrawn.")
    public ApiResponse<WalletTransactionResponse> debit(@Valid @RequestBody DebitRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.debit(mapper.toCommand(request))), "Wallet debited");
    }

    // -------------------------------------------------------------------- helpers

    /**
     * Whitelisted sorting. An unlisted property is a 400 rather than a silent fallback,
     * because a statement sorted by something other than what was asked for is a statement
     * someone will reconcile against.
     */
    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(),
                                                String.join(", ", new TreeSet<>(SORTABLE.keySet()))));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.desc("createdAt")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
