package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.BookingDebitCommand;
import com.courier.modules.finance.application.command.CodDeliveryDebitCommand;
import com.courier.modules.finance.application.command.CommissionCreditCommand;
import com.courier.modules.finance.application.command.CreditCommand;
import com.courier.modules.finance.application.command.DebitCommand;
import com.courier.modules.finance.application.command.DrsChargeCreditCommand;
import com.courier.modules.finance.application.command.RechargeCommand;
import com.courier.modules.finance.application.event.WalletEvent;
import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.PaymentStatus;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletNumberGenerator;
import com.courier.modules.finance.domain.WalletRepository;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.finance.domain.WalletTransactionCriteria;
import com.courier.modules.finance.domain.WalletTransactionRepository;
import com.courier.modules.finance.domain.WalletTransactionSpecifications;
import com.courier.modules.finance.infrastructure.CompanyPaymentGatewayResolver;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Branch wallet use cases.
 *
 * <p>Three things hold this module together, and none of them is negotiable.
 *
 * <ol>
 *   <li><b>Balance and ledger move together or not at all.</b> Every mutation goes through
 *       {@link #post}, which writes the {@link WalletTransaction} and applies the balance
 *       inside one transaction. There is no other path, and no method here sets a balance.</li>
 *   <li><b>The wallet row is locked, not versioned.</b> Money paths load through
 *       {@code lockByBranchIdWithinCompany} ({@code SELECT ... FOR UPDATE}). Two concurrent
 *       bookings against one branch serialise; with optimistic locking one would lose and a
 *       customer would see a failed payment for a race they had no part in.</li>
 *   <li><b>The amount credited comes from the gateway, never from the client.</b> See
 *       {@link #completeRecharge}.</li>
 * </ol>
 *
 * <p>Per-method {@code @PreAuthorize} gates the role tier; the finer "your branch" is
 * enforced in code, because it depends on the caller's own placement. Reads out of scope
 * answer 404 (don't reveal another branch exists); writes out of scope answer 403 (the
 * caller reached a branch of their own company they simply may not act on) — the same split
 * {@code BranchServiceImpl} makes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final String ENTITY = "Wallet";
    private static final String LEDGER_ENTITY = "WalletTransaction";

    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String AUTHENTICATED = "isAuthenticated()";

    /** Company-wide financial reach — restricted to the two roles the Finance Report's
     *  nav entry itself is gated on, not every company user with wallet read access. */
    private static final String FINANCE_READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN
            + "', '" + com.courier.modules.company.domain.DefaultRoleCatalog.FINANCE_USER + "')";

    /**
     * Moving a company's money is a company's own act.
     *
     * <p>A platform operator is excluded explicitly rather than by accident. They have no
     * branch, so {@code resolveBranchForWrite} would refuse them anyway — but "it happens
     * not to work" is not the same as "it is not allowed", and the day someone gives a
     * super admin a branch for testing, the accident stops protecting anything. A
     * recharge that a platform operator made is indistinguishable in the ledger from one
     * the branch made, which is precisely what a ledger exists to prevent.
     */
    private static final String COMPANY_USERS_ONLY =
            "isAuthenticated() and !hasRole('" + Roles.SUPER_ADMIN + "') "
                    + "and !hasRole('" + Roles.PLATFORM_ADMIN + "')";

    /** Wallet numbers are random; a collision is improbable, not impossible. */
    private static final int NUMBER_ATTEMPTS = 5;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final BranchDirectoryPort branchDirectory;
    private final CompanyPaymentGatewayResolver paymentGatewayResolver;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------- provisioning

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public Wallet getOrCreateForBranch(UUID branchId) {
        UUID companyId = requireCompany();
        requireBranchExists(branchId, companyId);

        Optional<Wallet> existing = walletRepository.findByBranchIdWithinCompany(branchId, companyId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Wallet wallet = Wallet.builder()
                .walletNumber(nextWalletNumber())
                .branchId(branchId)
                .status(com.courier.modules.finance.domain.WalletStatus.ACTIVE)
                .availableBalance(Wallet.ZERO)
                .holdBalance(Wallet.ZERO)
                .currency(Wallet.DEFAULT_CURRENCY)
                .build();

        // A concurrent first access could race us here. uk_wallets_company_branch is the
        // arbiter: the loser gets a DataIntegrityViolationException, which the global
        // handler renders as a 409, and the retry finds the wallet the winner created.
        // Rare by construction — this happens at most once in a branch's lifetime.
        Wallet saved = walletRepository.save(wallet);

        log.info("Wallet {} created for branch {} in company {}",
                saved.getWalletNumber(), branchId, companyId);
        auditService.record(AuditAction.WALLET_CREATED, ENTITY, saved.getId(),
                Map.of("walletNumber", saved.getWalletNumber(), "branchId", branchId.toString()));
        eventPublisher.publishEvent(new WalletEvent.WalletCreated(
                saved.getId(), companyId, branchId, saved.getWalletNumber(), Instant.now()));

        return saved;
    }

    // -------------------------------------------------------------------- reads

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public Wallet getForBranch(UUID branchId) {
        UUID companyId = requireCompany();
        UUID resolved = resolveBranchForRead(branchId, companyId);
        // Not read-only: a branch created before this module exists without a wallet, and
        // "your wallet does not exist" is not an answer a branch user can act on.
        return getOrCreateForBranch(resolved);
    }

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public WalletSummary summarise(UUID branchId) {
        UUID companyId = requireCompany();
        UUID resolved = resolveBranchForRead(branchId, companyId);
        return summariseResolved(resolved, companyId);
    }

    @Override
    @Transactional
    @PreAuthorize(FINANCE_READERS)
    public List<WalletSummary> companySummary() {
        UUID companyId = requireCompany();
        return branchDirectory.listBranches(companyId).stream()
                .map(b -> summariseResolved(b.branchId(), companyId))
                .toList();
    }

    /** Shared by {@link #summarise} and {@link #companySummary} — one branch's wallet
     *  summary, given an already-resolved/authorised branch id. */
    private WalletSummary summariseResolved(UUID resolved, UUID companyId) {
        Wallet wallet = getOrCreateForBranch(resolved);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        BranchDirectoryPort.BranchRef branch =
                branchDirectory.findBranch(resolved, companyId).orElse(null);

        WalletTransaction lastTransaction = firstOf(
                transactionRepository.findRecent(wallet.getId(), companyId, PageRequest.of(0, 1)));
        WalletTransaction lastRecharge = firstOf(
                transactionRepository.findLatestSettledOfType(wallet.getId(), companyId,
                        SubTransactionType.WRC, PaymentStatus.SUCCESS, PageRequest.of(0, 1)));

        return new WalletSummary(
                wallet,
                branch == null ? null : branch.branchCode(),
                branch == null ? null : branch.branchName(),
                sum(wallet, companyId, TransactionType.CR, startOfDay),
                sum(wallet, companyId, TransactionType.DR, startOfDay),
                sum(wallet, companyId, TransactionType.CR, startOfMonth),
                sum(wallet, companyId, TransactionType.DR, startOfMonth),
                sum(wallet, companyId, TransactionType.CR, Instant.EPOCH),
                sum(wallet, companyId, TransactionType.DR, Instant.EPOCH),
                transactionRepository.countByWallet(wallet.getId(), companyId),
                lastTransaction == null ? null : lastTransaction.getCreatedAt(),
                lastRecharge == null ? null : lastRecharge.getAmount(),
                lastRecharge == null ? null : lastRecharge.getCreatedAt());
    }

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public Page<WalletTransaction> searchTransactions(UUID branchId,
                                                      WalletTransactionCriteria criteria,
                                                      Pageable pageable) {
        UUID companyId = requireCompany();
        UUID resolved = resolveBranchForRead(branchId, companyId);
        Wallet wallet = getOrCreateForBranch(resolved);

        // The wallet and company are pinned here, never taken from the request: a criteria
        // object that arrived with someone else's wallet id would be someone else's statement.
        WalletTransactionCriteria safe =
                (criteria == null ? WalletTransactionCriteria.none() : criteria)
                        .scopedTo(wallet.getId(), companyId);

        return transactionRepository.findAll(WalletTransactionSpecifications.matching(safe), pageable);
    }

    // ----------------------------------------------------------------- recharge

    @Override
    @Transactional
    @PreAuthorize(COMPANY_USERS_ONLY)
    public PaymentGatewayPort.GatewayOrder openRecharge(RechargeCommand command) {
        UUID companyId = requireCompany();
        PaymentGatewayPort paymentGateway = paymentGatewayResolver.resolve(companyId);
        UUID branchId = resolveBranchForWrite(command.branchId(), companyId);
        Wallet wallet = getOrCreateForBranch(branchId);
        requireOperational(wallet);

        BigDecimal amount = requirePositiveAmount(command.amount());

        // No ledger row yet, on purpose. An entry is a record of money that moved; a
        // PENDING row for a checkout the user may simply close would make every statement
        // and every balance-before/after figure a guess. The intent is recorded in the
        // audit trail instead, which is where "who tried what" belongs.
        Map<String, String> notes = new HashMap<>();
        notes.put("walletNumber", wallet.getWalletNumber());
        notes.put("branchId", branchId.toString());
        notes.put("companyId", companyId.toString());

        PaymentGatewayPort.GatewayOrder order = paymentGateway.createOrder(
                amount, wallet.getCurrency(), WalletNumberGenerator.transactionNumber(), notes);

        log.info("Recharge order {} opened for wallet {} ({} {}) by {}",
                order.orderId(), wallet.getWalletNumber(), wallet.getCurrency(),
                amount.toPlainString(), currentActor());
        auditService.record(AuditAction.WALLET_RECHARGE_INITIATED, ENTITY, wallet.getId(),
                Map.of("walletNumber", wallet.getWalletNumber(),
                        "amount", amount.toPlainString(),
                        "gateway", order.gateway(),
                        "gatewayOrderId", order.orderId()));

        return order;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_USERS_ONLY)
    public WalletTransaction completeRecharge(RechargeCommand command) {
        UUID companyId = requireCompany();
        PaymentGatewayPort paymentGateway = paymentGatewayResolver.resolve(companyId);
        UUID branchId = resolveBranchForWrite(command.branchId(), companyId);

        String paymentId = required(command.paymentReference(), "paymentReference");
        String orderId = required(command.gatewayOrderId(), "gatewayOrderId");
        String signature = required(command.signature(), "signature");

        // Idempotency, before anything else. A client that retries, a double-submitted
        // form and a webhook that fires twice must all credit the wallet exactly once.
        Optional<WalletTransaction> already =
                transactionRepository.findByPaymentReferenceWithinCompany(paymentId, companyId);
        if (already.isPresent()) {
            log.info("Recharge {} already recorded as {}; returning it unchanged",
                    paymentId, already.get().getTransactionNo());
            return already.get();
        }
        // Not ours, but recorded by someone. Refuse flatly rather than saying whose it is.
        if (transactionRepository.isPaymentReferenceRecorded(paymentId)) {
            log.warn("Payment {} was presented by company {} but is already recorded elsewhere",
                    paymentId, companyId);
            throw new BusinessRuleException("This payment has already been recorded.");
        }

        // 1. The confirmation really came from the gateway.
        paymentGateway.verifyPayment(
                new PaymentGatewayPort.PaymentConfirmation(orderId, paymentId, signature));

        // 2. What was actually paid, according to the gateway — not according to the
        //    client. Verifying a signature proves the pair is genuine; it says nothing
        //    about the amount the browser claims alongside it.
        PaymentGatewayPort.GatewayPayment payment = paymentGateway.fetchPayment(paymentId);
        if (!payment.captured()) {
            throw new BusinessRuleException(
                    "The payment has not been captured by the gateway and cannot be credited.");
        }
        if (!orderId.equals(payment.orderId())) {
            log.warn("Payment {} belongs to order {}, not the presented {}",
                    paymentId, payment.orderId(), orderId);
            throw new BusinessRuleException("The payment does not belong to that order.");
        }

        Wallet wallet = lockOrThrow(branchId, companyId);
        requireOperational(wallet);
        if (payment.currency() != null && !payment.currency().equalsIgnoreCase(wallet.getCurrency())) {
            throw new BusinessRuleException(
                    "The payment was made in %s but the wallet holds %s."
                            .formatted(payment.currency(), wallet.getCurrency()));
        }

        WalletTransaction entry = post(wallet, companyId, TransactionType.CR,
                SubTransactionType.WRC, payment.amount(),
                ReferenceType.PAYMENT, orderId, command.remarks(),
                payment.gateway(), paymentId, PaymentStatus.SUCCESS);

        log.info("Wallet {} recharged {} {} via {} ({}) by {}",
                wallet.getWalletNumber(), wallet.getCurrency(),
                payment.amount().toPlainString(), payment.gateway(), paymentId, currentActor());
        auditService.record(AuditAction.WALLET_RECHARGED, ENTITY, wallet.getId(),
                Map.of("walletNumber", wallet.getWalletNumber(),
                        "transactionNo", entry.getTransactionNo(),
                        "amount", payment.amount().toPlainString(),
                        "gateway", payment.gateway(),
                        "paymentReference", paymentId,
                        "balanceAfter", entry.getBalanceAfter().toPlainString()));
        eventPublisher.publishEvent(new WalletEvent.WalletRecharged(
                wallet.getId(), companyId, branchId, entry.getId(), payment.gateway(), paymentId,
                payment.amount(), entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ------------------------------------------------------------ manual credit

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public WalletTransaction credit(CreditCommand command) {
        UUID companyId = requireCompany();
        UUID branchId = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal amount = requirePositiveAmount(command.amount());
        SubTransactionType reason = command.subTransactionType() == null
                ? SubTransactionType.MCR : command.subTransactionType();
        reason.requireSupports(TransactionType.CR);

        Wallet wallet = lockOrThrow(branchId, companyId);
        requireOperational(wallet);

        WalletTransaction entry = post(wallet, companyId, TransactionType.CR, reason, amount,
                command.referenceType() == null ? ReferenceType.MANUAL : command.referenceType(),
                command.referenceId(), command.remarks(), null, null, null);

        log.info("Wallet {} credited {} {} ({}) by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), amount.toPlainString(), reason, currentActor());
        auditService.record(AuditAction.WALLET_CREDITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletCredited(
                wallet.getId(), companyId, branchId, entry.getId(), reason, amount,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ------------------------------------------------------------- manual debit

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public WalletTransaction debit(DebitCommand command) {
        UUID companyId = requireCompany();
        UUID branchId = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal amount = requirePositiveAmount(command.amount());
        SubTransactionType reason = command.subTransactionType() == null
                ? SubTransactionType.MDB : command.subTransactionType();
        reason.requireSupports(TransactionType.DR);

        Wallet wallet = lockOrThrow(branchId, companyId);
        requireOperational(wallet);

        // Wallet.applyDebit refuses an overdraft; the ledger entry is only written if it
        // succeeds, so a rejected debit leaves no trace of a balance that never changed.
        WalletTransaction entry = post(wallet, companyId, TransactionType.DR, reason, amount,
                command.referenceType() == null ? ReferenceType.MANUAL : command.referenceType(),
                command.referenceId(), command.remarks(), null, null, null);

        log.info("Wallet {} debited {} {} ({}) by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), amount.toPlainString(), reason, currentActor());
        auditService.record(AuditAction.WALLET_DEBITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletDebited(
                wallet.getId(), companyId, branchId, entry.getId(), reason, amount,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ------------------------------------------------------------- COD delivery debit

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public WalletTransaction debitForCodDelivery(CodDeliveryDebitCommand command) {
        UUID companyId = requireCompany();
        UUID resolvedBranch = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal validated = requirePositiveAmount(command.amount());
        SubTransactionType reason = SubTransactionType.COD;
        reason.requireSupports(TransactionType.DR);

        Wallet wallet = lockOrThrow(resolvedBranch, companyId);
        requireOperational(wallet);

        WalletTransaction entry = post(wallet, companyId, TransactionType.DR, reason, validated,
                ReferenceType.SHIPMENT, command.shipmentNumber(), command.remarks(), null, null,
                null);

        log.info("Wallet {} debited {} {} for COD delivery {} by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), validated.toPlainString(), command.shipmentNumber(),
                currentActor());
        auditService.record(AuditAction.WALLET_DEBITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletDebited(
                wallet.getId(), companyId, resolvedBranch, entry.getId(), reason, validated,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ------------------------------------------------------------- DRS charge credit

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public WalletTransaction creditForDrsCharge(DrsChargeCreditCommand command) {
        UUID companyId = requireCompany();
        UUID resolvedBranch = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal validated = requirePositiveAmount(command.amount());
        SubTransactionType reason = SubTransactionType.DRS;
        reason.requireSupports(TransactionType.CR);

        Wallet wallet = lockOrThrow(resolvedBranch, companyId);
        requireOperational(wallet);

        WalletTransaction entry = post(wallet, companyId, TransactionType.CR, reason, validated,
                ReferenceType.SHIPMENT, command.shipmentNumber(), command.remarks(), null, null,
                null);

        log.info("Wallet {} credited {} {} DRS commission for shipment {} by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), validated.toPlainString(), command.shipmentNumber(),
                currentActor());
        auditService.record(AuditAction.WALLET_CREDITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletCredited(
                wallet.getId(), companyId, resolvedBranch, entry.getId(), reason, validated,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // -------------------------------------------------------------- booking debit

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public WalletTransaction debitForBooking(BookingDebitCommand command) {
        UUID companyId = requireCompany();
        UUID resolvedBranch = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal validated = requirePositiveAmount(command.amount());
        SubTransactionType reason = SubTransactionType.SBK;
        reason.requireSupports(TransactionType.DR);

        Wallet wallet = lockOrThrow(resolvedBranch, companyId);
        requireOperational(wallet);

        WalletTransaction entry = post(wallet, companyId, TransactionType.DR, reason, validated,
                ReferenceType.SHIPMENT, command.shipmentNumber(), command.remarks(), null, null,
                null);

        log.info("Wallet {} debited {} {} for booking {} by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), validated.toPlainString(), command.shipmentNumber(),
                currentActor());
        auditService.record(AuditAction.WALLET_DEBITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletDebited(
                wallet.getId(), companyId, resolvedBranch, entry.getId(), reason, validated,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ---------------------------------------------------------- commission credit

    @Override
    @Transactional
    @PreAuthorize(AUTHENTICATED)
    public WalletTransaction creditCommission(CommissionCreditCommand command) {
        UUID companyId = requireCompany();
        UUID resolvedBranch = resolveBranchForWrite(command.branchId(), companyId);

        BigDecimal validated = requirePositiveAmount(command.amount());
        SubTransactionType reason = SubTransactionType.COM;
        reason.requireSupports(TransactionType.CR);

        Wallet wallet = lockOrThrow(resolvedBranch, companyId);
        requireOperational(wallet);

        WalletTransaction entry = post(wallet, companyId, TransactionType.CR, reason, validated,
                ReferenceType.SHIPMENT, command.shipmentNumber(), command.remarks(), null, null,
                null);

        log.info("Wallet {} credited {} {} commission for booking {} by {}", wallet.getWalletNumber(),
                wallet.getCurrency(), validated.toPlainString(), command.shipmentNumber(),
                currentActor());
        auditService.record(AuditAction.WALLET_CREDITED, ENTITY, wallet.getId(),
                ledgerDetails(wallet, entry));
        eventPublisher.publishEvent(new WalletEvent.WalletCredited(
                wallet.getId(), companyId, resolvedBranch, entry.getId(), reason, validated,
                entry.getBalanceAfter(), Instant.now()));

        return entry;
    }

    // ------------------------------------------------------- the only money path

    /**
     * Applies the balance change and writes the matching ledger entry, in that order and in
     * one transaction.
     *
     * <p>The order matters: {@code applyCredit} / {@code applyDebit} enforce the domain rules
     * (positive amount, operational wallet, no overdraft) and throw before anything is
     * written, so a refused operation leaves neither a balance change nor an orphan entry.
     */
    private WalletTransaction post(Wallet wallet, UUID companyId,
                                   TransactionType type, SubTransactionType reason,
                                   BigDecimal amount,
                                   ReferenceType referenceType, String referenceId, String remarks,
                                   String gateway, String paymentReference,
                                   PaymentStatus paymentStatus) {

        BigDecimal before = Wallet.normalise(wallet.getAvailableBalance());
        BigDecimal after = type.isCredit() ? wallet.applyCredit(amount) : wallet.applyDebit(amount);

        WalletTransaction entry = WalletTransaction.builder()
                .transactionNo(nextTransactionNumber())
                .walletId(wallet.getId())
                .transactionType(type)
                .subTransactionType(reason)
                .amount(Wallet.normalise(amount))
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .remarks(remarks)
                .paymentGateway(gateway)
                .paymentReference(paymentReference)
                .paymentStatus(paymentStatus)
                .build();

        WalletTransaction saved = transactionRepository.save(entry);
        walletRepository.save(wallet);
        return saved;
    }

    // ------------------------------------------------------------------ scoping

    /**
     * Which branch's wallet the caller is asking to read.
     *
     * <p>An admin may name any branch of their company. Anyone else gets their own — the
     * branch they are placed at, or the one they manage — and naming another branch answers
     * 404, because "that branch exists but is not yours" is information they should not have.
     */
    private UUID resolveBranchForRead(UUID requested, UUID companyId) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (isAdmin(caller)) {
            UUID branchId = requested != null ? requested : ownBranch(caller, companyId).orElse(null);
            if (branchId == null) {
                throw new BusinessRuleException(
                        "Name a branch: you are not assigned to one, so there is no wallet to default to.");
            }
            requireBranchExists(branchId, companyId);
            return branchId;
        }

        UUID own = ownBranch(caller, companyId).orElseThrow(() -> new BusinessRuleException(
                "You are not assigned to a branch, so you have no wallet to view."));
        if (requested != null && !requested.equals(own)) {
            throw new ResourceNotFoundException(ENTITY, requested);
        }
        return own;
    }

    /**
     * Which branch's wallet the caller is asking to change.
     *
     * <p>403 rather than 404 here, mirroring {@code BranchServiceImpl.requireManageable}: the
     * caller reached a branch of their own company that they may not act on, which is a
     * permission answer, not a hidden resource.
     */
    private UUID resolveBranchForWrite(UUID requested, UUID companyId) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (isAdmin(caller)) {
            UUID branchId = requested != null ? requested : ownBranch(caller, companyId).orElse(null);
            if (branchId == null) {
                throw new BusinessRuleException(
                        "Name the branch whose wallet this affects.");
            }
            requireBranchExists(branchId, companyId);
            return branchId;
        }

        UUID own = ownBranch(caller, companyId).orElseThrow(() -> new BusinessRuleException(
                "You are not assigned to a branch, so you have no wallet to transact on."));
        if (requested != null && !requested.equals(own)) {
            throw new ForbiddenException("You may only transact on the wallet of your own branch.");
        }
        return own;
    }

    /** The branch a non-admin caller belongs to: their placement, else the one they manage. */
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

    // ------------------------------------------------------------------ helpers

    private Wallet lockOrThrow(UUID branchId, UUID companyId) {
        // Ensure the wallet exists before taking the row lock — FOR UPDATE on no rows
        // locks nothing and would let two callers both proceed to create one.
        getOrCreateForBranch(branchId);
        return walletRepository.lockByBranchIdWithinCompany(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, branchId));
    }

    private void requireBranchExists(UUID branchId, UUID companyId) {
        if (branchId == null) {
            throw new BusinessRuleException("A branch is required.");
        }
        branchDirectory.findBranch(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
    }

    private void requireOperational(Wallet wallet) {
        if (!wallet.isActive()) {
            throw new BusinessRuleException(
                    "Wallet %s is %s and cannot accept transactions."
                            .formatted(wallet.getWalletNumber(),
                                    wallet.getStatus().name().toLowerCase()));
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero.");
        }
        return Wallet.normalise(amount);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("'%s' is required to settle a recharge.".formatted(field));
        }
        return value.trim();
    }

    /** Null (no matching rows) normalises to zero — a wallet with no history has no total. */
    private BigDecimal sum(Wallet wallet, UUID companyId, TransactionType type, Instant from) {
        return Wallet.normalise(transactionRepository.sumSettledSince(
                wallet.getId(), companyId, type, from, PaymentStatus.SUCCESS));
    }

    private static WalletTransaction firstOf(List<WalletTransaction> rows) {
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private String nextWalletNumber() {
        for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
            String candidate = WalletNumberGenerator.walletNumber();
            if (!walletRepository.existsByWalletNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique wallet number in " + NUMBER_ATTEMPTS + " attempts");
    }

    private String nextTransactionNumber() {
        for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
            String candidate = WalletNumberGenerator.transactionNumber();
            if (!transactionRepository.existsByTransactionNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique transaction number in " + NUMBER_ATTEMPTS + " attempts");
    }

    private Map<String, Object> ledgerDetails(Wallet wallet, WalletTransaction entry) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("walletNumber", wallet.getWalletNumber());
        details.put("transactionNo", entry.getTransactionNo());
        details.put("transactionType", entry.getTransactionType().name());
        details.put("subTransactionType", entry.getSubTransactionType().name());
        details.put("amount", entry.getAmount().toPlainString());
        details.put("balanceBefore", entry.getBalanceBefore().toPlainString());
        details.put("balanceAfter", entry.getBalanceAfter().toPlainString());
        details.put("referenceId", entry.getReferenceId());
        return details;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. A wallet belongs to a branch of a "
                        + "company, so this operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
