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
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyImportServicePreviewTest {

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentChargeRepository charges = mock(ShipmentChargeRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final LegacyOriginBranchRepository origins = mock(LegacyOriginBranchRepository.class);
    private final LegacyImportService service = new LegacyImportService(shipments, charges, branches, origins,
            mock(com.courier.modules.legacyimport.domain.LegacyImportBatchRepository.class),
            mock(com.courier.modules.master.domain.PaymentModeRepository.class),
            mock(com.courier.modules.master.domain.ServiceTypeRepository.class),
            mock(LegacyShipmentWriter.class), mock(com.courier.shared.audit.application.AuditService.class));
    private final UUID company = TimeOrderedUuid.generate();

    @BeforeEach
    void bind() {
        CompanyContext.setCompanyId(company);
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of());
        when(charges.findByShipmentIdIn(any())).thenReturn(List.of());
        when(origins.findAllByCompanyId(company)).thenReturn(List.of());
    }

    @AfterEach
    void unbind() {
        CompanyContext.clear();
    }

    private static String tr(String date, String no, String consignee, String from, String to, String pkg,
                             String weight, String total) {
        return "<tr><td>" + date + "</td><td>" + no + "</td><td>C</td><td>" + consignee + "</td><td>" + from
                + "</td><td>" + to + "</td><td>" + pkg + "</td><td>" + weight + "</td><td>" + total + "</td></tr>";
    }

    private static byte[] file(String... rows) {
        return ("<table><tr><th>Date</th><th>Consignment No</th><th>Consignor</th><th>Consignee</th><th>From</th>"
                + "<th>To</th><th>Package</th><th>Actual Weight</th><th>Total</th></tr>" + String.join("", rows)
                + "</table>").getBytes(StandardCharsets.UTF_8);
    }

    private static Shipment existing(String no, String consignee, String toCity, int pkgs, String weight) {
        Shipment s = Shipment.builder().trackingNumber(no).shipmentNumber(no)
                .bookingDate(LocalDate.of(2026, 11, 15)).receiverName(consignee).toCity(toCity)
                .numberOfPackages(pkgs).actualWeight(new BigDecimal(weight)).build();
        return s;
    }

    @Test
    void newVsExisting() {
        Shipment s = existing("A1", "RAM", "PUNE", 1, "3.82");
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));

        PreviewResult r = service.preview("a.xls", file(
                tr("15/11/2026", "A1", "RAM", "BHOSARI", "PUNE", "1", "3.82", "0"),
                tr("15/11/2026", "A2", "SHAM", "BHOSARI", "PUNE", "1", "3.82", "0")));

        assertThat(r.rows()).extracting(PreviewRow::outcome).containsExactly(RowOutcome.EXISTING, RowOutcome.NEW);
        assertThat(r.newCount()).isEqualTo(1);
        assertThat(r.existingCount()).isEqualTo(1);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.fileHash()).hasSize(64);
    }

    @Test
    void existingWithSameValuesHasNoDifferences() {
        Shipment s = existing("A1", "RAM ", "pune", 1, "3.820");
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));

        PreviewResult r = service.preview("a.xls", file(tr("15/11/2026", "A1", "RAM", "BHOSARI", "PUNE", "1", "3.82", "0")));

        assertThat(r.rows().get(0).differences()).isEmpty();
        assertThat(r.differsCount()).isZero();
    }

    @Test
    void existingWithDifferentWeightListsDifference() {
        Shipment s = existing("A1", "RAM", "PUNE", 1, "9.00");
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));

        PreviewResult r = service.preview("a.xls", file(tr("15/11/2026", "A1", "RAM", "BHOSARI", "PUNE", "1", "3.82", "0")));

        assertThat(r.rows().get(0).differences()).hasSize(1).first().asString().startsWith("weight");
        assertThat(r.differsCount()).isEqualTo(1);
    }

    @Test
    void existingTotalIsComparedWithChargeNetAmount() {
        Shipment s = existing("A1", "RAM", "PUNE", 1, "3.82");
        ShipmentCharge c = new ShipmentCharge();
        c.setShipmentId(s.getId());
        c.setNetAmount(new BigDecimal("10.00"));
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));
        when(charges.findByShipmentIdIn(any())).thenReturn(List.of(c));

        PreviewResult r = service.preview("a.xls", file(tr("15/11/2026", "A1", "RAM", "BHOSARI", "PUNE", "1", "3.82", "57.30")));

        assertThat(r.rows().get(0).differences()).hasSize(1).first().asString().startsWith("total");
    }

    @Test
    void duplicateInFileBothInvalid() {
        PreviewResult r = service.preview("a.xls", file(
                tr("15/11/2026", "D1", "X", "PUNE", "SATARA", "1", "1", "1"),
                tr("16/11/2026", "D1", "Y", "PUNE", "SATARA", "1", "1", "1")));

        assertThat(r.rows()).extracting(PreviewRow::outcome).containsExactly(RowOutcome.INVALID, RowOutcome.INVALID);
        assertThat(r.rows().get(0).reason()).contains("Duplicate");
        assertThat(r.invalidCount()).isEqualTo(2);
    }

    @Test
    void invalidRowKeepsParserReason() {
        PreviewResult r = service.preview("a.xls", file(tr("bad", "B1", "X", "PUNE", "SATARA", "1", "1", "1")));

        assertThat(r.rows().get(0).outcome()).isEqualTo(RowOutcome.INVALID);
        assertThat(r.rows().get(0).reason()).contains("date");
    }

    @Test
    void originKeyNormalisesWhitespaceAndCase() {
        assertThat(LegacyImportService.originKey("SANGALI ")).isEqualTo("SANGALI");
        assertThat(LegacyImportService.originKey(" Sangali")).isEqualTo("SANGALI");

        UUID branch = TimeOrderedUuid.generate();
        LegacyOriginBranch map = LegacyOriginBranch.builder().originKey("BHOSARI").branchId(branch).build();
        when(origins.findAllByCompanyId(company)).thenReturn(List.of(map));

        PreviewResult r = service.preview("a.xls", file(
                tr("15/11/2026", "N1", "X", "bhosari ", "PUNE", "1", "1", "1"),
                tr("15/11/2026", "N2", "X", "SANGALI ", "PUNE", "1", "1", "1"),
                tr("15/11/2026", "N3", "X", "SANGALI", "PUNE", "1", "1", "1")));

        assertThat(r.origins()).extracting(OriginSummary::originKey, OriginSummary::newRows, OriginSummary::mappedBranchId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("BHOSARI", 1, branch),
                        org.assertj.core.api.Assertions.tuple("SANGALI", 2, null));
    }

    @Test
    void previewWritesNothing() {
        service.preview("a.xls", file(tr("15/11/2026", "N1", "X", "PUNE", "SATARA", "1", "1", "1")));

        verify(shipments, never()).save(any());
        verify(origins, never()).save(any());
    }

    @Test
    void saveOriginMappingRejectsForeignCompanyBranch() {
        UUID branchId = TimeOrderedUuid.generate();
        when(branches.findByIdWithinCompany(branchId, company)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveOriginMapping("BHOSARI", branchId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(origins, never()).save(any());
    }

    @Test
    void saveOriginMappingRejectsBranchThatCannotBook() {
        UUID branchId = TimeOrderedUuid.generate();
        Branch hub = mock(Branch.class);
        when(hub.isActive()).thenReturn(true);
        when(hub.isAllowBooking()).thenReturn(false);
        when(hub.getBranchCode()).thenReturn("HB-X");
        when(branches.findByIdWithinCompany(branchId, company)).thenReturn(Optional.of(hub));

        assertThatThrownBy(() -> service.saveOriginMapping("BHOSARI", branchId))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("HB-X");
    }

    @Test
    void saveOriginMappingUpsertsByNormalisedKey() {
        UUID branchId = TimeOrderedUuid.generate();
        Branch ok = mock(Branch.class);
        when(ok.isActive()).thenReturn(true);
        when(ok.isAllowBooking()).thenReturn(true);
        when(branches.findByIdWithinCompany(branchId, company)).thenReturn(Optional.of(ok));
        LegacyOriginBranch existingMap = LegacyOriginBranch.builder().originKey("BHOSARI")
                .branchId(TimeOrderedUuid.generate()).build();
        when(origins.findByCompanyIdAndOriginKey(company, "BHOSARI")).thenReturn(Optional.of(existingMap));

        service.saveOriginMapping(" bhosari ", branchId);

        assertThat(existingMap.getBranchId()).isEqualTo(branchId);
        verify(origins).save(existingMap);
    }
}
