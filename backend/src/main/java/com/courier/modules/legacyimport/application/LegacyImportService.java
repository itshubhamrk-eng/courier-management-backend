package com.courier.modules.legacyimport.application;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.legacyimport.domain.LegacyOriginBranch;
import com.courier.modules.legacyimport.domain.LegacyOriginBranchRepository;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Brings the old software's consignment export in as history-only shipments.
 *
 * <p><b>Preview</b> classifies every row and writes nothing. A consignment number that is
 * already a tracking number in the company is EXISTING and is never touched — differences
 * are only reported. Everything else is NEW (bookable) or INVALID (reason given).
 * See {@code docs/superpowers/specs/2026-11-18-legacy-consignment-import-design.md}.
 */
@Service
@RequiredArgsConstructor
public class LegacyImportService {

    private static final int LOOKUP_CHUNK = 500;

    private final ShipmentRepository shipmentRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final BranchRepository branchRepository;
    private final LegacyOriginBranchRepository originRepository;

    public static String originKey(String from) {
        return from == null ? "" : from.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(String fileName, byte[] bytes) {
        UUID companyId = requireCompany();
        List<LegacyRow> parsed = LegacyFileParser.parse(fileName, bytes);
        List<PreviewRow> rows = classify(companyId, parsed);

        Map<String, UUID> mapping = originRepository.findAllByCompanyId(companyId).stream()
                .collect(Collectors.toMap(LegacyOriginBranch::getOriginKey, LegacyOriginBranch::getBranchId,
                        (a, b) -> a));
        Map<String, Integer> newPerOrigin = new LinkedHashMap<>();
        rows.stream().filter(r -> r.outcome() == RowOutcome.NEW)
                .forEach(r -> newPerOrigin.merge(r.origin(), 1, Integer::sum));
        List<OriginSummary> origins = newPerOrigin.entrySet().stream()
                .map(e -> new OriginSummary(e.getKey(), e.getValue(), mapping.get(e.getKey())))
                .sorted((a, b) -> Integer.compare(b.newRows(), a.newRows()))
                .toList();

        return new PreviewResult(sha256(bytes), rows.size(), count(rows, RowOutcome.NEW),
                count(rows, RowOutcome.EXISTING),
                (int) rows.stream().filter(r -> r.outcome() == RowOutcome.EXISTING && !r.differences().isEmpty()).count(),
                count(rows, RowOutcome.INVALID), origins, rows);
    }

    /** Remembers which of our branches a legacy "From" text books into. */
    @Transactional
    public void saveOriginMapping(String originText, UUID branchId) {
        UUID companyId = requireCompany();
        String key = originKey(originText);
        if (key.isEmpty()) {
            throw new BusinessRuleException("An origin is required.");
        }
        Branch branch = branchRepository.findByIdWithinCompany(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
        if (!branch.isActive() || !branch.isAllowBooking()) {
            throw new BusinessRuleException("Branch %s is not an active booking branch.".formatted(branch.getBranchCode()));
        }
        LegacyOriginBranch row = originRepository.findByCompanyIdAndOriginKey(companyId, key)
                .orElseGet(() -> LegacyOriginBranch.builder().originKey(key).build());
        row.setBranchId(branchId);
        originRepository.save(row);
    }

    // ------------------------------------------------------------------ classification

    List<PreviewRow> classify(UUID companyId, List<LegacyRow> parsed) {
        Map<String, Long> occurrences = parsed.stream().filter(r -> !r.consignmentNo().isEmpty())
                .collect(Collectors.groupingBy(LegacyRow::consignmentNo, Collectors.counting()));
        Map<String, Shipment> existing = loadExisting(companyId, parsed);
        Map<UUID, ShipmentCharge> charges = chargeRepository.findByShipmentIdIn(
                        existing.values().stream().map(Shipment::getId).toList()).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, c -> c, (a, b) -> a));

        List<PreviewRow> out = new ArrayList<>(parsed.size());
        for (LegacyRow r : parsed) {
            String origin = originKey(r.from());
            if (r.error() != null) {
                out.add(new PreviewRow(r.rowNo(), r.consignmentNo(), RowOutcome.INVALID, r.error(), List.of(), origin));
            } else if (occurrences.get(r.consignmentNo()) > 1) {
                out.add(new PreviewRow(r.rowNo(), r.consignmentNo(), RowOutcome.INVALID,
                        "Duplicate consignment number in file", List.of(), origin));
            } else if (existing.containsKey(r.consignmentNo())) {
                Shipment s = existing.get(r.consignmentNo());
                out.add(new PreviewRow(r.rowNo(), r.consignmentNo(), RowOutcome.EXISTING, null,
                        differences(r, s, charges.get(s.getId())), origin));
            } else if (origin.isEmpty()) {
                out.add(new PreviewRow(r.rowNo(), r.consignmentNo(), RowOutcome.INVALID,
                        "Origin (From) is blank", List.of(), origin));
            } else {
                out.add(new PreviewRow(r.rowNo(), r.consignmentNo(), RowOutcome.NEW, null, List.of(), origin));
            }
        }
        return out;
    }

    private Map<String, Shipment> loadExisting(UUID companyId, List<LegacyRow> parsed) {
        List<String> numbers = parsed.stream().map(LegacyRow::consignmentNo).filter(n -> !n.isEmpty())
                .distinct().toList();
        Map<String, Shipment> found = new HashMap<>();
        for (int i = 0; i < numbers.size(); i += LOOKUP_CHUNK) {
            List<String> chunk = numbers.subList(i, Math.min(i + LOOKUP_CHUNK, numbers.size()));
            shipmentRepository.findAllByCompanyIdAndTrackingNumberIn(companyId, chunk)
                    .forEach(s -> found.put(s.getTrackingNumber(), s));
        }
        return found;
    }

    private static List<String> differences(LegacyRow r, Shipment s, ShipmentCharge charge) {
        List<String> d = new ArrayList<>();
        if (!r.date().equals(s.getBookingDate())) {
            d.add("date: ours %s / file %s".formatted(s.getBookingDate(), r.date()));
        }
        if (!same(r.consignee(), s.getReceiverName())) {
            d.add("consignee: ours %s / file %s".formatted(s.getReceiverName(), r.consignee()));
        }
        if (!same(r.to(), s.getToCity())) {
            d.add("destination: ours %s / file %s".formatted(s.getToCity(), r.to()));
        }
        if (s.getNumberOfPackages() == null || r.packages() != s.getNumberOfPackages()) {
            d.add("packages: ours %s / file %d".formatted(s.getNumberOfPackages(), r.packages()));
        }
        if (s.getActualWeight().compareTo(r.weight()) != 0) {
            d.add("weight: ours %s / file %s".formatted(s.getActualWeight().toPlainString(), r.weight().toPlainString()));
        }
        if (charge != null && charge.getNetAmount().compareTo(r.total()) != 0) {
            d.add("total: ours %s / file %s".formatted(charge.getNetAmount().toPlainString(), r.total().toPlainString()));
        }
        return d;
    }

    private static boolean same(String a, String b) {
        return (a == null ? "" : a.trim()).equalsIgnoreCase(b == null ? "" : b.trim());
    }

    private static int count(List<PreviewRow> rows, RowOutcome outcome) {
        return (int) rows.stream().filter(r -> r.outcome() == outcome).count();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
    }
}
