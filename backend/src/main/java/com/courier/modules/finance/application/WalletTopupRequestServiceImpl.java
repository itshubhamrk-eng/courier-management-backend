package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.CreateTopupRequestCommand;
import com.courier.modules.finance.application.command.CreditCommand;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TopupRequestStatus;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletTopupRequest;
import com.courier.modules.finance.domain.WalletTopupRequestCriteria;
import com.courier.modules.finance.domain.WalletTopupRequestRepository;
import com.courier.modules.finance.domain.WalletTopupRequestSpecifications;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * See {@link WalletTopupRequestService}. Mirrors {@code WalletServiceImpl}'s own
 * branch-scoping shape (an admin names a branch, anyone else gets their own; reads out of
 * scope answer 404, writes 403) so a branch manager's request workflow behaves exactly like
 * every other branch-scoped write in this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTopupRequestServiceImpl implements WalletTopupRequestService {

    private static final String ENTITY = "WalletTopupRequest";

    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    /** A branch raises its own request; a company admin may raise one on a branch's behalf. */
    private static final String BRANCH_WRITERS =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.BRANCH_MANAGER + "')";

    private final WalletTopupRequestRepository repository;
    private final WalletService walletService;
    private final BranchDirectoryPort branchDirectory;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public WalletTopupRequest create(CreateTopupRequestCommand command) {
        UUID companyId = requireCompany();
        UUID branchId = resolveBranchForWrite(command.branchId(), companyId);
        BigDecimal amount = requirePositiveAmount(command.amount());

        Wallet wallet = walletService.getOrCreateForBranch(branchId);
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();

        WalletTopupRequest request = WalletTopupRequest.builder()
                .walletId(wallet.getId())
                .branchId(branchId)
                .requestedAmount(amount)
                .remarks(trimOrNull(command.remarks()))
                .status(TopupRequestStatus.PENDING)
                .requestedBy(caller.userId())
                .build();

        WalletTopupRequest saved = repository.save(request);

        log.info("Top-up request {} raised for wallet {} ({} {}) by {}",
                saved.getId(), wallet.getWalletNumber(), wallet.getCurrency(),
                amount.toPlainString(), currentActor());
        auditService.record(AuditAction.WALLET_TOPUP_REQUESTED, ENTITY, saved.getId(),
                Map.of("walletNumber", wallet.getWalletNumber(),
                        "branchId", branchId.toString(),
                        "amount", amount.toPlainString()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public WalletTopupRequest getById(UUID id) {
        UUID companyId = requireCompany();
        WalletTopupRequest request = repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        requireReadable(request, companyId);
        return request;
    }

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public Page<WalletTopupRequest> search(WalletTopupRequestCriteria criteria, Pageable pageable) {
        UUID companyId = requireCompany();
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();

        WalletTopupRequestCriteria safe =
                (criteria == null ? WalletTopupRequestCriteria.none() : criteria).scopedTo(companyId);

        if (!isAdmin(caller)) {
            // A branch caller only ever sees their own branch's requests, regardless of
            // what (if anything) they asked to filter by.
            UUID own = ownBranch(caller, companyId).orElseThrow(() -> new BusinessRuleException(
                    "You are not assigned to a branch, so you have no requests to view."));
            safe = new WalletTopupRequestCriteria(companyId, own, safe.status());
        }

        return repository.findAll(WalletTopupRequestSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public WalletTopupRequest approve(UUID id, String decisionRemarks) {
        UUID companyId = requireCompany();
        WalletTopupRequest request = repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        requirePending(request);

        WalletTransaction entry = walletService.credit(new CreditCommand(
                request.getBranchId(), request.getRequestedAmount(), SubTransactionType.MCR,
                ReferenceType.MANUAL, request.getId().toString(),
                "Top-up request approved" + (hasText(decisionRemarks) ? ": " + decisionRemarks.trim() : "")));

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        request.setStatus(TopupRequestStatus.APPROVED);
        request.setDecidedBy(caller.userId());
        request.setDecidedAt(Instant.now());
        request.setDecisionRemarks(trimOrNull(decisionRemarks));
        request.setTransactionId(entry.getId());
        WalletTopupRequest saved = repository.save(request);

        log.info("Top-up request {} approved, credited {} by {}",
                saved.getId(), entry.getAmount().toPlainString(), currentActor());
        auditService.record(AuditAction.WALLET_TOPUP_APPROVED, ENTITY, saved.getId(),
                Map.of("branchId", saved.getBranchId().toString(),
                        "amount", saved.getRequestedAmount().toPlainString(),
                        "transactionNo", entry.getTransactionNo()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public WalletTopupRequest reject(UUID id, String decisionRemarks) {
        UUID companyId = requireCompany();
        WalletTopupRequest request = repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        requirePending(request);

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        request.setStatus(TopupRequestStatus.REJECTED);
        request.setDecidedBy(caller.userId());
        request.setDecidedAt(Instant.now());
        request.setDecisionRemarks(trimOrNull(decisionRemarks));
        WalletTopupRequest saved = repository.save(request);

        log.info("Top-up request {} rejected by {}", saved.getId(), currentActor());
        auditService.record(AuditAction.WALLET_TOPUP_REJECTED, ENTITY, saved.getId(),
                Map.of("branchId", saved.getBranchId().toString(),
                        "amount", saved.getRequestedAmount().toPlainString()));

        return saved;
    }

    // ------------------------------------------------------------------ scoping

    private UUID resolveBranchForWrite(UUID requested, UUID companyId) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (isAdmin(caller)) {
            UUID branchId = requested != null ? requested : ownBranch(caller, companyId).orElse(null);
            if (branchId == null) {
                throw new BusinessRuleException("Name the branch this request is for.");
            }
            requireBranchExists(branchId, companyId);
            return branchId;
        }

        UUID own = ownBranch(caller, companyId).orElseThrow(() -> new BusinessRuleException(
                "You are not assigned to a branch, so you have no wallet to request a top-up for."));
        if (requested != null && !requested.equals(own)) {
            throw new ForbiddenException("You may only request a top-up for your own branch.");
        }
        return own;
    }

    private void requireReadable(WalletTopupRequest request, UUID companyId) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (isAdmin(caller)) {
            return;
        }
        UUID own = ownBranch(caller, companyId).orElseThrow(() -> new BusinessRuleException(
                "You are not assigned to a branch, so you have no requests to view."));
        if (!own.equals(request.getBranchId())) {
            throw new ResourceNotFoundException(ENTITY, request.getId());
        }
    }

    private Optional<UUID> ownBranch(AuthenticatedUser caller, UUID companyId) {
        Optional<UUID> placement = branchDirectory.branchOfUser(caller.userId(), companyId);
        if (placement.isPresent()) {
            return placement;
        }
        return branchDirectory.branchManagedBy(caller.userId(), companyId);
    }

    private boolean isAdmin(AuthenticatedUser caller) {
        return caller.hasRole(Roles.COMPANY_ADMIN) || caller.isSuperAdmin();
    }

    private void requireBranchExists(UUID branchId, UUID companyId) {
        if (branchId == null) {
            throw new BusinessRuleException("A branch is required.");
        }
        branchDirectory.findBranch(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
    }

    // ------------------------------------------------------------------ helpers

    private static void requirePending(WalletTopupRequest request) {
        if (!request.isPending()) {
            throw new BusinessRuleException(
                    "This request is already %s and cannot be decided again."
                            .formatted(request.getStatus().name().toLowerCase()));
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero.");
        }
        return Wallet.normalise(amount);
    }

    private static String trimOrNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. A top-up request belongs to a branch "
                        + "of a company, so this operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
