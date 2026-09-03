package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.api.dto.ImportRowResult;
import com.courier.modules.districtfreight.api.dto.ImportSummaryResponse;
import com.courier.modules.districtfreight.application.command.CreateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.application.command.UpdateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.domain.BranchLookupPort;
import com.courier.modules.districtfreight.domain.DistrictLevelFreight;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightRepository;
import com.courier.modules.districtfreight.domain.DistrictLookupPort;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulk rate setup from the company's existing District Level Freight spreadsheet.
 *
 * <p><b>Row selection.</b> A row counts as data only when it has a non-blank From Station,
 * a non-blank District, <i>and</i> all six slab cells parse as numbers — every other row
 * (a blank spacer, a header repeated lower in the sheet, or the trailing "* ODA charge
 * Rs.250 extra..." note row this format carries) is silently ignored rather than reported,
 * since none of those are a rate the operator meant to set.
 *
 * <p><b>Upsert.</b> An From Station + District combination already on file is updated in
 * place (its rates/ODA replaced), never rejected as a duplicate — re-uploading a corrected
 * sheet is the normal workflow, not an edge case. Only a combination repeated <i>within
 * the same file</i> is a real error: which of the two rows should win is ambiguous, so
 * neither is applied.
 *
 * <p><b>Per-row transactions.</b> {@link #commit} calls {@link DistrictLevelFreightService}
 * (a cross-bean call) per row rather than wrapping the whole file in one transaction — one
 * bad row fails on its own and every other row still commits, the same reasoning
 * {@code PincodeBulkImportService} documents for its own per-row {@code create} calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictLevelFreightExcelImportService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    /** The six slabs, as the (low, high) KG pair a header cell must contain — in either
     *  order, since real sheets are not consistent about it (e.g. "51 KG TO  KG 100"
     *  instead of "51 KG TO 100 KG"). Matched by extracting every number in the header
     *  text, not by comparing the text itself, because word order, spacing and even which
     *  side the unit sits on vary sheet to sheet. */
    private static final Map<List<Integer>, String> SLAB_BOUNDARIES = Map.of(
            List.of(1, 15), "rate1To15",
            List.of(16, 50), "rate16To50",
            List.of(51, 100), "rate51To100",
            List.of(101, 1000), "rate101To1000",
            List.of(1001, 1500), "rate1001To1500",
            List.of(1501, 2000), "rate1501To2000");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "branch", "district", "rate1To15", "rate16To50", "rate51To100",
            "rate101To1000", "rate1001To1500", "rate1501To2000");

    private final DistrictLevelFreightRepository repository;
    private final DistrictLevelFreightService service;
    private final BranchLookupPort branchLookup;
    private final DistrictLookupPort districtLookup;

    @Transactional(readOnly = true)
    @PreAuthorize(WRITE)
    public ImportSummaryResponse preview(MultipartFile file) {
        return run(file, true);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PreAuthorize(WRITE)
    public ImportSummaryResponse commit(MultipartFile file) {
        return run(file, false);
    }

    // -------------------------------------------------------------------- pipeline

    private ImportSummaryResponse run(MultipartFile file, boolean dryRun) {
        UUID companyId = CompanyContext.requireCompanyId();
        List<ParsedRow> parsed = parse(file);

        List<ImportRowResult> results = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        int succeeded = 0;
        int failed = 0;

        for (ParsedRow row : parsed) {
            try {
                BranchLookupPort.BranchRef branch = branchLookup.findBranchByLabel(row.branchLabel, companyId)
                        .orElseThrow(() -> new BusinessRuleException(
                                "No branch matches From Station \"" + row.branchLabel + "\"."));
                if (!branch.active()) {
                    throw new BusinessRuleException("Branch \"" + branch.branchName() + "\" is inactive.");
                }
                DistrictLookupPort.DistrictRef district = districtLookup.findDistrictByName(row.districtLabel)
                        .orElseThrow(() -> new BusinessRuleException(
                                "No district matches \"" + row.districtLabel + "\"."));
                if (!district.active()) {
                    throw new BusinessRuleException("District \"" + district.name() + "\" is inactive.");
                }

                String comboKey = branch.branchId() + "|" + district.districtId();
                if (!seenInFile.add(comboKey)) {
                    throw new BusinessRuleException(
                            "Duplicate From Station + District in this file: " + branch.branchName()
                                    + " + " + district.name() + ".");
                }

                var existing = repository.findByCompanyIdAndBranchIdAndDistrictId(
                        companyId, branch.branchId(), district.districtId());
                String outcome = applyRow(row, branch.branchId(), district.districtId(), existing.orElse(null), dryRun);
                results.add(new ImportRowResult(row.rowNumber, row.branchLabel, row.districtLabel, outcome, null));
                succeeded++;
            } catch (BusinessRuleException e) {
                results.add(new ImportRowResult(row.rowNumber, row.branchLabel, row.districtLabel, "ERROR", e.getMessage()));
                failed++;
            }
        }

        log.info("District Level Freight Excel import ({}) in company {}: {} row(s), {} ok, {} failed",
                dryRun ? "preview" : "commit", companyId, parsed.size(), succeeded, failed);
        return new ImportSummaryResponse(dryRun, parsed.size(), succeeded, failed, results);
    }

    private String applyRow(ParsedRow row, UUID branchId, UUID districtId,
                             DistrictLevelFreight existing, boolean dryRun) {
        if (existing == null) {
            if (!dryRun) {
                service.create(new CreateDistrictLevelFreightCommand(
                        branchId, districtId, row.rate1To15, row.rate16To50, row.rate51To100,
                        row.rate101To1000, row.rate1001To1500, row.rate1501To2000,
                        true, row.odaCharge));
            }
            return dryRun ? "WOULD_CREATE" : "CREATED";
        }
        if (!dryRun) {
            service.update(existing.getId(), new UpdateDistrictLevelFreightCommand(
                    branchId, districtId, row.rate1To15, row.rate16To50, row.rate51To100,
                    row.rate101To1000, row.rate1001To1500, row.rate1501To2000,
                    existing.isOdaApplicable(), row.odaCharge == null ? existing.getOdaCharge() : row.odaCharge,
                    existing.getVersion()));
        }
        return dryRun ? "WOULD_UPDATE" : "UPDATED";
    }

    // --------------------------------------------------------------------- parsing

    private record ParsedRow(
            int rowNumber, String branchLabel, String districtLabel,
            BigDecimal rate1To15, BigDecimal rate16To50, BigDecimal rate51To100,
            BigDecimal rate101To1000, BigDecimal rate1001To1500, BigDecimal rate1501To2000,
            BigDecimal odaCharge) {
    }

    private List<ParsedRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose an Excel file to import.");
        }
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<Integer, String> columns = readHeader(sheet, formatter);

            List<ParsedRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row excelRow = sheet.getRow(r);
                if (excelRow == null) {
                    continue;
                }
                Map<String, String> cells = new LinkedHashMap<>();
                columns.forEach((colIndex, purpose) -> cells.put(purpose, cellText(excelRow.getCell(colIndex), formatter)));

                String branchLabel = cells.getOrDefault("branch", "");
                String districtLabel = cells.getOrDefault("district", "");
                if (branchLabel.isBlank() || districtLabel.isBlank()) {
                    continue; // blank spacer row or a note row with no station/district
                }

                BigDecimal r1 = tryParseDecimal(cells.get("rate1To15"));
                BigDecimal r2 = tryParseDecimal(cells.get("rate16To50"));
                BigDecimal r3 = tryParseDecimal(cells.get("rate51To100"));
                BigDecimal r4 = tryParseDecimal(cells.get("rate101To1000"));
                BigDecimal r5 = tryParseDecimal(cells.get("rate1001To1500"));
                BigDecimal r6 = tryParseDecimal(cells.get("rate1501To2000"));
                if (r1 == null || r2 == null || r3 == null || r4 == null || r5 == null || r6 == null) {
                    // Has a station/district label but not six numeric rates — the ODA note
                    // row reads exactly this way when the note text lands in this column.
                    continue;
                }
                BigDecimal oda = tryParseDecimal(cells.get("odaCharge"));

                rows.add(new ParsedRow(r + 1, branchLabel.trim(), districtLabel.trim(),
                        r1, r2, r3, r4, r5, r6, oda));
            }
            if (rows.isEmpty()) {
                throw new BusinessRuleException(
                        "No data rows found. Check the sheet has a header row and at least one rate row.");
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the Excel file: " + e.getMessage());
        }
    }

    private Map<Integer, String> readHeader(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            throw new BusinessRuleException("The sheet has no header row.");
        }
        Map<Integer, String> columns = new LinkedHashMap<>();
        for (Cell cell : header) {
            String text = cellText(cell, formatter);
            String purpose = classifyHeader(text);
            if (purpose != null) {
                columns.put(cell.getColumnIndex(), purpose);
            }
        }
        Set<String> found = new HashSet<>(columns.values());
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !found.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "The sheet is missing expected column(s): " + String.join(", ", missing)
                            + ". Expected From Station, District, and the six weight-slab rate columns.");
        }
        return columns;
    }

    /**
     * A header's purpose, or {@code null} if it names none of the columns this import
     * understands. From Station/District/ODA are matched by keyword (case-insensitive,
     * punctuation-insensitive); the six rate columns are matched by the pair of numbers the
     * header names — not the surrounding words — so "51 KG TO 100 KG" and "51 KG TO  KG 100"
     * (a real variant seen in an actual company's sheet) both resolve to the same slab.
     */
    private static String classifyHeader(String raw) {
        String normalised = normaliseHeader(raw);
        if (normalised.isEmpty()) {
            return null;
        }
        if (normalised.contains("STATION") || normalised.equals("BRANCH")) {
            return "branch";
        }
        if (normalised.contains("DISTRICT")) {
            return "district";
        }
        if (normalised.contains("ODA")) {
            return "odaCharge";
        }
        List<Integer> numbers = extractNumbers(raw);
        if (numbers.size() == 2) {
            int lo = Math.min(numbers.get(0), numbers.get(1));
            int hi = Math.max(numbers.get(0), numbers.get(1));
            return SLAB_BOUNDARIES.get(List.of(lo, hi));
        }
        return null;
    }

    private static List<Integer> extractNumbers(String raw) {
        if (raw == null) {
            return List.of();
        }
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(raw);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers;
    }

    private static String normaliseHeader(String raw) {
        return raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static BigDecimal tryParseDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String cleaned = text.replace(",", "").replace("₹", "").trim();
            BigDecimal value = new BigDecimal(cleaned);
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
