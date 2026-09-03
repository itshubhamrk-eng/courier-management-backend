package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.api.dto.ImportSummaryResponse;
import com.courier.modules.districtfreight.domain.BranchLookupPort;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightRepository;
import com.courier.modules.districtfreight.domain.DistrictLookupPort;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers the real-world header format a live upload actually failed on: a company's own
 * sheet named its 51-100 KG column {@code "51 KG TO  KG 100"} (unit and number swapped,
 * an extra space) rather than the spec's own {@code "51 KG TO 100 KG"}. Exact-text header
 * matching rejected the whole file; {@code classifyHeader} now matches by the pair of
 * numbers a header names, in either order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistrictLevelFreightExcelImportServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID DISTRICT_PUNE = UUID.randomUUID();
    private static final UUID DISTRICT_SATARA = UUID.randomUUID();

    private static final BranchLookupPort.BranchRef KARAD =
            new BranchLookupPort.BranchRef(BRANCH, "KARAD", "Karad", true);
    private static final DistrictLookupPort.DistrictRef PUNE =
            new DistrictLookupPort.DistrictRef(DISTRICT_PUNE, "PUNE", "Pune", true);
    private static final DistrictLookupPort.DistrictRef SATARA =
            new DistrictLookupPort.DistrictRef(DISTRICT_SATARA, "SATARA", "Satara", true);

    @Mock private DistrictLevelFreightRepository repository;
    @Mock private DistrictLevelFreightService service;
    @Mock private BranchLookupPort branchLookup;
    @Mock private DistrictLookupPort districtLookup;

    private DistrictLevelFreightExcelImportService importService;

    @BeforeEach
    void setUp() {
        importService = new DistrictLevelFreightExcelImportService(repository, service, branchLookup, districtLookup);
        CompanyContext.setCompanyId(COMPANY);
        when(branchLookup.findBranchByLabel(eq("KARAD"), eq(COMPANY))).thenReturn(Optional.of(KARAD));
        when(districtLookup.findDistrictByName("PUNE")).thenReturn(Optional.of(PUNE));
        when(districtLookup.findDistrictByName("SATARA")).thenReturn(Optional.of(SATARA));
        when(repository.findByCompanyIdAndBranchIdAndDistrictId(any(), any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("a real sheet's own header wording (number/unit swapped, blank row, ODA note row) parses correctly")
    void parsesRealWorldHeaderVariant() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "KARAD RATE.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildRealWorldSheet());

        ImportSummaryResponse result = importService.preview(file);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.totalDataRows()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.rows()).extracting("outcome").containsExactly("WOULD_CREATE", "WOULD_CREATE");
        assertThat(result.rows()).extracting("district").containsExactly("PUNE", "SATARA");
    }

    @Test
    @DisplayName("a sheet missing a required column is refused with a clear message")
    void refusesSheetMissingAColumn() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("From station");
            header.createCell(1).setCellValue("District");
            header.createCell(2).setCellValue("1KG TO 15 KG");
            // Every other slab column is missing.
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("KARAD");
            data.createCell(1).setCellValue("PUNE");
            data.createCell(2).setCellValue(10);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "bad.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            assertThatThrownBy(() -> importService.preview(file))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("missing expected column");
        }
    }

    /** Mirrors the real file's own shape: header row 1, data rows 2-3, a blank spacer row
     *  4, and the trailing "ODA CHARGES" / "RS.250/- EXTRA" note row 5 with no slab data. */
    private byte[] buildRealWorldSheet() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();

            Row header = sheet.createRow(0);
            List<String> headers = List.of("From station", "District", "1KG TO 15 KG", "16 KG TO 50KG",
                    "51 KG TO  KG 100", "101 KG TO 1000 KG", "1001 KG TO 1500 KG", "1501 KG TO 2000KG");
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }

            writeDataRow(sheet, 1, "KARAD", "PUNE", 10, 8, 7.5, 6, 5.5, 5);
            writeDataRow(sheet, 2, "KARAD", "SATARA", 5, 4.5, 4, 3, 3, 3);

            // Blank spacer row (row 4 has no cells at all).
            sheet.createRow(3);

            Row note = sheet.createRow(4);
            note.createCell(0).setCellValue("ODA CHARGES");
            note.createCell(1).setCellValue("RS.250/- EXTRA");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeDataRow(Sheet sheet, int rowIndex, String branch, String district, double... rates) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(branch);
        row.createCell(1).setCellValue(district);
        for (int i = 0; i < rates.length; i++) {
            row.createCell(2 + i).setCellValue(rates[i]);
        }
    }
}
