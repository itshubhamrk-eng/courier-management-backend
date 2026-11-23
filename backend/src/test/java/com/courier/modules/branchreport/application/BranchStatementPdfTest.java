package com.courier.modules.branchreport.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.shipment.domain.VendorAuditRow;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchStatementPdfTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static List<WalletTransaction> txns(int n) {
        List<WalletTransaction> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            WalletTransaction t = WalletTransaction.builder().transactionNo("TXN" + String.format("%06d", i))
                    .transactionType(TransactionType.DR).subTransactionType(SubTransactionType.SBK)
                    .amount(d("10")).balanceBefore(d("1000")).balanceAfter(d("990"))
                    .referenceType(ReferenceType.SHIPMENT).referenceId("SHP" + i).build();
            t.setCreatedAt(Instant.parse("2026-10-05T10:00:00Z"));
            list.add(t);
        }
        return list;
    }

    private static BranchStatement statement(int n) {
        UUID b = UUID.randomUUID();
        VendorAuditRow audit = new VendorAuditRow(b, 3, 7, d("300"), 2, 4, d("200"), d("30"), d("77.77"),
                5, 4, d("30"), d("77.77"), d("5"), d("6"), 1, null, null, null, null, null);
        WalletService.PeriodSummary w = new WalletService.PeriodSummary(d("100"), d("1234.50"), 2, d("500"),
                d("12"), d("3"), d("20"), d("300"));
        return new BranchStatement(b, "BR01", "Pune Central", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), audit, w, txns(n));
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader r = new PdfReader(pdf);
        PdfTextExtractor x = new PdfTextExtractor(r);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= r.getNumberOfPages(); i++) {
            sb.append(x.getTextFromPage(i)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void rendersHeaderSummaryAndMovementsByTypeOnOnePage() throws Exception {
        byte[] pdf = BranchStatementPdf.render(statement(5), "Amazing Logistics");
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
        String t = text(pdf);
        assertThat(t).contains("Pune Central").contains("BR01").contains("Amazing Logistics")
                .contains("01 Oct 2026 to 31 Oct 2026").contains("1,234.50")
                .contains("Wallet movements by type").contains("Shipment Booking");
    }

    @Test
    void embedsTheCompanyLogoWhenGivenOne() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        byte[] with = BranchStatementPdf.render(statement(1), "Amazing Logistics", png.toByteArray());
        byte[] without = BranchStatementPdf.render(statement(1), "Amazing Logistics", null);
        assertThat(with.length).isGreaterThan(without.length);
        assertThat(new PdfReader(with).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void anUnreadableLogoFallsBackToTheNameOnlyHeader() throws Exception {
        byte[] pdf = BranchStatementPdf.render(statement(1), "Amazing Logistics", new byte[] {1, 2, 3, 4});
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
        assertThat(text(pdf)).contains("Amazing Logistics");
    }

    @Test
    void deliveryCommissionComesFromLedgerOnly() throws Exception {
        String t = text(BranchStatementPdf.render(statement(1), "Amazing Logistics"));
        assertThat(t).contains("Delivery (DRS) commission credited").contains("20.00");
        assertThat(t).doesNotContain("77.77");
    }

    @Test
    void everyTransactionTypeAtOnceStillFitsOnOnePage() throws Exception {
        UUID b = UUID.randomUUID();
        VendorAuditRow audit = new VendorAuditRow(b, 3, 7, d("300"), 2, 4, d("200"), d("30"), d("77.77"),
                5, 4, d("30"), d("77.77"), d("5"), d("6"), 1, null, null, null, null, null);
        WalletService.PeriodSummary w = new WalletService.PeriodSummary(d("100"), d("1234.50"), 2, d("500"),
                d("12"), d("3"), d("20"), d("300"));
        List<WalletTransaction> all = new ArrayList<>();
        for (SubTransactionType type : SubTransactionType.values()) {
            WalletTransaction t = WalletTransaction.builder().transactionNo("T" + type.name())
                    .transactionType(TransactionType.DR).subTransactionType(type)
                    .amount(d("10")).balanceBefore(d("1000")).balanceAfter(d("990"))
                    .referenceType(ReferenceType.SHIPMENT).referenceId("X").build();
            t.setCreatedAt(Instant.parse("2026-10-05T10:00:00Z"));
            all.add(t);
        }
        BranchStatement s = new BranchStatement(b, "BR01", "Pune Central", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), audit, w, all);
        assertThat(new PdfReader(BranchStatementPdf.render(s, "Amazing Logistics")).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void aBusyMonthStillFitsOnOnePageAndRendersQuickly() throws Exception {
        long start = System.nanoTime();
        byte[] pdf = BranchStatementPdf.render(statement(5000), "Amazing Logistics");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(30));
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
    }
}
