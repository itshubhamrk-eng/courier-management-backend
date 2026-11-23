package com.courier.modules.branchreport.application;

import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.BranchDirectoryPort.BranchRef;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.VendorAuditRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** No {@code @PreAuthorize}: runs on a scheduler thread inside the caller's company context. */
@Service
public class BranchStatementServiceImpl implements BranchStatementService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ShipmentService shipmentService;
    private final WalletService walletService;
    private final BranchDirectoryPort branchDirectory;

    public BranchStatementServiceImpl(ShipmentService shipmentService, WalletService walletService,
                                      BranchDirectoryPort branchDirectory) {
        this.shipmentService = shipmentService;
        this.walletService = walletService;
        this.branchDirectory = branchDirectory;
    }

    /** Send-all and the monthly job call {@link #build} once per branch; the company-wide audit
     *  behind each call loads every shipment in the range, so it is computed once and shared
     *  for a short window instead of once per branch. Short enough that an on-demand statement
     *  never shows figures older than a minute. */
    private static final long AUDIT_TTL_MS = 60_000;

    private record AuditKey(UUID companyId, LocalDate from, LocalDate to) { }

    private AuditKey cachedKey;
    private Map<UUID, VendorAuditRow> cachedAudit;
    private long cachedAt;

    @Override
    public List<BranchStatement> buildAll(UUID companyId, LocalDate from, LocalDate to) {
        Map<UUID, VendorAuditRow> audit = auditRows(companyId, from, to);
        return branchDirectory.listBranches(companyId).stream()
                .map(ref -> assemble(companyId, ref, from, to, audit.get(ref.branchId())))
                .toList();
    }

    @Override
    public Optional<BranchStatement> build(UUID companyId, UUID branchId, LocalDate from, LocalDate to) {
        Optional<BranchRef> ref = branchDirectory.findBranch(branchId, companyId);
        if (ref.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assemble(companyId, ref.get(), from, to, auditRows(companyId, from, to).get(branchId)));
    }

    private synchronized Map<UUID, VendorAuditRow> auditRows(UUID companyId, LocalDate from, LocalDate to) {
        AuditKey key = new AuditKey(companyId, from, to);
        long now = System.currentTimeMillis();
        if (key.equals(cachedKey) && now - cachedAt < AUDIT_TTL_MS) {
            return cachedAudit;
        }
        Map<UUID, VendorAuditRow> fresh = loadAuditRows(from, to);
        cachedKey = key;
        cachedAudit = fresh;
        cachedAt = now;
        return fresh;
    }

    private Map<UUID, VendorAuditRow> loadAuditRows(LocalDate from, LocalDate to) {
        ShipmentCriteria criteria = new ShipmentCriteria(null, null, null, null, null, null, from, to,
                null, null, null, null, null, null);
        Map<UUID, VendorAuditRow> byBranch = new HashMap<>();
        for (VendorAuditRow row : shipmentService.vendorAuditUnguarded(criteria)) {
            byBranch.put(row.branchId(), row);
        }
        return byBranch;
    }

    private BranchStatement assemble(UUID companyId, BranchRef ref, LocalDate from, LocalDate to,
                                     VendorAuditRow audit) {
        Instant start = from.atStartOfDay(IST).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(IST).toInstant();
        WalletService.PeriodSummary wallet =
                walletService.periodSummaryUnguarded(companyId, ref.branchId(), start, end);
        VendorAuditRow row = audit != null ? audit : zeroRow(ref.branchId());
        return new BranchStatement(ref.branchId(), ref.branchCode(), ref.branchName(), from, to, row,
                wallet, walletService.transactionsBetween(companyId, ref.branchId(), start, end));
    }

    private static VendorAuditRow zeroRow(UUID branchId) {
        BigDecimal z = BigDecimal.ZERO;
        return new VendorAuditRow(branchId, 0, 0, z, 0, 0, z, z, z, 0, 0, z, z, z, z, 0,
                null, null, null, null, null);
    }
}
