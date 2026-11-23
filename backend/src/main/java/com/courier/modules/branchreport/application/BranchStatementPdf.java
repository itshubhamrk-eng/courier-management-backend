package com.courier.modules.branchreport.application;

import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.shipment.domain.VendorAuditRow;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a {@link BranchStatement}: a branded first page (header band, key figures, wallet
 * reconciliation, orders and commission) followed by the full transaction ledger with running
 * balance. Every page carries a footer with the page number.
 */
public final class BranchStatementPdf {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH).withZone(IST);

    private static final Color INK = new Color(0x1E, 0x29, 0x3B);
    private static final Color BRAND = new Color(0xB3, 0x12, 0x3A);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color PANEL = new Color(0xF1, 0xF5, 0xF9);
    private static final Color ZEBRA = new Color(0xF8, 0xFA, 0xFC);
    private static final Color RULE = new Color(0xE2, 0xE8, 0xF0);
    private static final Color CREDIT = new Color(0x15, 0x80, 0x3D);
    private static final Color DEBIT = new Color(0xB9, 0x1C, 0x1C);

    private static Font font(String face, float size, Color color) {
        int style = FontFactory.HELVETICA_BOLD.equals(face) ? Font.BOLD
                : FontFactory.HELVETICA_OBLIQUE.equals(face) ? Font.ITALIC : Font.NORMAL;
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static final Font H_COMPANY = font(FontFactory.HELVETICA_BOLD, 20, Color.WHITE);
    private static final Font H_SUB = font(FontFactory.HELVETICA, 9, new Color(0xFC, 0xE7, 0xEC));
    private static final Font H_PERIOD = font(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
    private static final Font LABEL = font(FontFactory.HELVETICA, 8, MUTED);
    private static final Font VALUE = font(FontFactory.HELVETICA_BOLD, 11, INK);
    private static final Font KPI = font(FontFactory.HELVETICA_BOLD, 16, INK);
    private static final Font SECTION = font(FontFactory.HELVETICA_BOLD, 11, BRAND);
    private static final Font BODY = font(FontFactory.HELVETICA, 9, INK);
    private static final Font BOLD = font(FontFactory.HELVETICA_BOLD, 9, INK);
    private static final Font SMALL = font(FontFactory.HELVETICA, 8, INK);
    private static final Font TH = font(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font NOTE = font(FontFactory.HELVETICA_OBLIQUE, 7.5f, MUTED);

    private BranchStatementPdf() {
    }

    public static byte[] render(BranchStatement s, String companyName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(companyName, s.branchName()));
            doc.open();
            header(doc, s, companyName);
            keyFigures(doc, s);
            walletSection(doc, s);
            ordersSection(doc, s);
            appendix(doc, s);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render branch statement", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ page 1

    private static void header(Document doc, BranchStatement s, String companyName) throws DocumentException {
        PdfPTable band = new PdfPTable(new float[] {3f, 2f});
        band.setWidthPercentage(100);
        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(BRAND);
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(14f);
        left.addElement(new Paragraph(companyName, H_COMPANY));
        left.addElement(new Paragraph("BRANCH MONTHLY STATEMENT", H_SUB));
        band.addCell(left);
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(BRAND);
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(14f);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph p = new Paragraph(DAY.format(s.from()) + " to " + DAY.format(s.to()), H_PERIOD);
        p.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(p);
        Paragraph g = new Paragraph("Generated on " + DAY.format(LocalDate.now(IST)), H_SUB);
        g.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(g);
        band.addCell(right);
        doc.add(band);

        PdfPTable info = new PdfPTable(new float[] {1f, 1f, 1f});
        info.setWidthPercentage(100);
        info.setSpacingBefore(10f);
        info.addCell(infoCell("BRANCH", s.branchName()));
        info.addCell(infoCell("BRANCH CODE", s.branchCode()));
        info.addCell(infoCell("STATEMENT PERIOD", DAY.format(s.from()) + " to " + DAY.format(s.to())));
        doc.add(info);
    }

    private static PdfPCell infoCell(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(PANEL);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBorderWidthLeft(3f);
        c.setBorderColorLeft(BRAND);
        c.setPadding(8f);
        c.addElement(new Paragraph(label, LABEL));
        c.addElement(new Paragraph(value == null ? "" : value, VALUE));
        return c;
    }

    private static void keyFigures(Document doc, BranchStatement s) throws DocumentException {
        VendorAuditRow a = s.audit();
        WalletService.PeriodSummary w = s.wallet();
        BigDecimal earned = nz(a.bookingTotalCommission()).add(nz(w.drsChargeCredited()));
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingBefore(12f);
        t.addCell(kpi("Closing balance", "Rs " + money(w.closingBalance())));
        t.addCell(kpi("Orders booked", String.valueOf(a.totalBookedOrderCount())));
        t.addCell(kpi("Orders delivered", String.valueOf(a.totalDeliveredOrderCount())));
        t.addCell(kpi("Commission earned", "Rs " + money(earned)));
        doc.add(t);
    }

    private static PdfPCell kpi(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(RULE);
        c.setBorderWidth(1f);
        c.setPadding(9f);
        c.addElement(new Paragraph(label, LABEL));
        Paragraph v = new Paragraph(value, KPI);
        v.setSpacingBefore(2f);
        c.addElement(v);
        return c;
    }

    private static void section(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, SECTION);
        p.setSpacingBefore(16f);
        p.setSpacingAfter(5f);
        doc.add(p);
    }

    private static void walletSection(Document doc, BranchStatement s) throws DocumentException {
        WalletService.PeriodSummary w = s.wallet();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        long debitCount = 0;
        long creditCount = 0;
        java.util.Map<String, BigDecimal[]> byType = new java.util.LinkedHashMap<>();
        java.util.Map<String, long[]> countByType = new java.util.HashMap<>();
        for (WalletTransaction x : s.transactions()) {
            String type = x.getSubTransactionType() == null ? "Other" : x.getSubTransactionType().getLabel();
            BigDecimal[] sums = byType.computeIfAbsent(type, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            long[] cnt = countByType.computeIfAbsent(type, k -> new long[1]);
            cnt[0]++;
            if (x.isDebit()) {
                debit = debit.add(nz(x.getAmount()));
                debitCount++;
                sums[0] = sums[0].add(nz(x.getAmount()));
            } else if (x.isCredit()) {
                credit = credit.add(nz(x.getAmount()));
                creditCount++;
                sums[1] = sums[1].add(nz(x.getAmount()));
            }
        }

        section(doc, "Wallet account");
        PdfPTable t = new PdfPTable(new float[] {4f, 2f});
        t.setWidthPercentage(100);
        ledgerRow(t, "Opening balance", money(w.openingBalance()), BOLD, PANEL);
        ledgerRow(t, "Total credits (" + creditCount + " transactions)", "+ " + money(credit), BODY, null);
        ledgerRow(t, "Total debits (" + debitCount + " transactions)", "- " + money(debit), BODY, null);
        ledgerRow(t, "Closing balance", money(w.closingBalance()), BOLD, PANEL);
        doc.add(t);

        section(doc, "Key wallet items");
        PdfPTable k = new PdfPTable(new float[] {4f, 2f});
        k.setWidthPercentage(100);
        ledgerRow(k, "Wallet recharges (" + w.rechargeCount() + ")", money(w.rechargeAmount()), BODY, null);
        ledgerRow(k, "Booking charges debited", money(w.bookingDebited()), BODY, ZEBRA);
        ledgerRow(k, "Commission credited", money(w.commissionCredited()), BODY, null);
        ledgerRow(k, "Delivery (DRS) commission credited", money(w.drsChargeCredited()), BODY, ZEBRA);
        ledgerRow(k, "Commission debited", money(w.commissionDebited()), BODY, null);
        doc.add(k);

        if (!byType.isEmpty()) {
            section(doc, "Wallet movements by type");
            PdfPTable m = new PdfPTable(new float[] {3.4f, 1f, 1.6f, 1.6f});
            m.setWidthPercentage(100);
            header4(m, "Type", "Count", "Debits (Rs)", "Credits (Rs)");
            int i = 0;
            for (var e : byType.entrySet()) {
                Color bg = i++ % 2 == 1 ? ZEBRA : null;
                m.addCell(cell(e.getKey(), BODY, Element.ALIGN_LEFT, bg));
                m.addCell(cell(String.valueOf(countByType.get(e.getKey())[0]), BODY, Element.ALIGN_RIGHT, bg));
                BigDecimal[] sums = e.getValue();
                m.addCell(cell(sums[0].signum() == 0 ? "" : money(sums[0]),
                        font(FontFactory.HELVETICA, 9, DEBIT), Element.ALIGN_RIGHT, bg));
                m.addCell(cell(sums[1].signum() == 0 ? "" : money(sums[1]),
                        font(FontFactory.HELVETICA, 9, CREDIT), Element.ALIGN_RIGHT, bg));
            }
            doc.add(m);
        }
    }

    private static void ordersSection(Document doc, BranchStatement s) throws DocumentException {
        VendorAuditRow a = s.audit();
        section(doc, "Orders and charges");
        PdfPTable t = new PdfPTable(new float[] {3f, 1f, 1f, 1.6f});
        t.setWidthPercentage(100);
        header4(t, "", "Orders", "Qty", "Amount (Rs)");
        row4(t, "Paid orders", a.paidOrderCount() + "", a.paidOrderQuantity() + "", money(a.paidOrderAmount()), false);
        row4(t, "To-Pay orders", a.topayOrderCount() + "", a.topayOrderQuantity() + "", money(a.topayOrderAmount()), true);
        row4(t, "Booked (all)", a.totalBookedOrderCount() + "", "", "", false);
        row4(t, "Delivered", a.totalDeliveredOrderCount() + "", "", "", true);
        row4(t, "Cancelled", a.cancelledOrderCount() + "", "", "", false);
        row4(t, "ODA charges", "", "", money(a.odaCharges()), true);
        row4(t, "Other charges", "", "", money(a.otherCharges()), false);
        row4(t, "Booking commission", "", "", money(a.bookingTotalCommission()), true);
        doc.add(t);
        Paragraph note = new Paragraph("Delivery commission is shown as credited in the wallet ledger above.", NOTE);
        note.setSpacingBefore(4f);
        doc.add(note);
    }

    private static void header4(PdfPTable t, String a, String b, String c, String d) {
        for (String h : new String[] {a, b, c, d}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TH));
            cell.setBackgroundColor(INK);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(5f);
            cell.setHorizontalAlignment(h.equals(a) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(cell);
        }
    }

    private static void row4(PdfPTable t, String a, String b, String c, String d, boolean shaded) {
        String[] v = {a, b, c, d};
        for (int i = 0; i < v.length; i++) {
            t.addCell(cell(v[i], i == 0 ? BODY : BODY, i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT,
                    shaded ? ZEBRA : null));
        }
    }

    private static void ledgerRow(PdfPTable t, String label, String value, Font font, Color bg) {
        t.addCell(cell(label, font, Element.ALIGN_LEFT, bg));
        t.addCell(cell(value, font, Element.ALIGN_RIGHT, bg));
    }

    // ---------------------------------------------------------------- appendix

    private static void appendix(Document doc, BranchStatement s) throws DocumentException {
        doc.newPage();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (WalletTransaction x : s.transactions()) {
            if (x.isDebit()) {
                debit = debit.add(nz(x.getAmount()));
            } else if (x.isCredit()) {
                credit = credit.add(nz(x.getAmount()));
            }
        }
        Paragraph title = new Paragraph("Transaction ledger", SECTION);
        title.setSpacingAfter(2f);
        doc.add(title);
        Paragraph sub = new Paragraph(s.transactions().size() + " transactions, " + DAY.format(s.from()) + " to "
                + DAY.format(s.to()) + "  |  Total debits Rs " + money(debit) + "  |  Total credits Rs "
                + money(credit), NOTE);
        sub.setSpacingAfter(6f);
        doc.add(sub);

        PdfPTable t = new PdfPTable(new float[] {2.2f, 2.3f, 2.7f, 2.7f, 1.6f, 1.6f, 1.9f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        String[] heads = {"Date (IST)", "Txn no", "Type", "Reference", "Debit", "Credit", "Balance"};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell h = new PdfPCell(new Phrase(heads[i], TH));
            h.setBackgroundColor(INK);
            h.setBorder(Rectangle.NO_BORDER);
            h.setPadding(5f);
            h.setHorizontalAlignment(i >= 4 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(h);
        }
        int n = 0;
        for (WalletTransaction x : s.transactions()) {
            Color bg = n++ % 2 == 1 ? ZEBRA : null;
            t.addCell(cell(x.getCreatedAt() == null ? "" : STAMP.format(x.getCreatedAt()), SMALL,
                    Element.ALIGN_LEFT, bg));
            t.addCell(cell(str(x.getTransactionNo()), SMALL, Element.ALIGN_LEFT, bg));
            t.addCell(cell(x.getSubTransactionType() == null ? "" : x.getSubTransactionType().getLabel(),
                    SMALL, Element.ALIGN_LEFT, bg));
            t.addCell(cell((x.getReferenceType() == null ? "" : x.getReferenceType().name())
                    + (x.getReferenceId() == null ? "" : " " + x.getReferenceId()), SMALL, Element.ALIGN_LEFT, bg));
            t.addCell(cell(x.isDebit() ? money(x.getAmount()) : "",
                    font(FontFactory.HELVETICA, 8, DEBIT), Element.ALIGN_RIGHT, bg));
            t.addCell(cell(x.isCredit() ? money(x.getAmount()) : "",
                    font(FontFactory.HELVETICA, 8, CREDIT), Element.ALIGN_RIGHT, bg));
            t.addCell(cell(money(x.getBalanceAfter()), SMALL, Element.ALIGN_RIGHT, bg));
            // Flush completed rows to the writer so a big ledger is not held as one page-less table.
            if (t.getRows().size() >= 200) {
                doc.add(t);
                t.deleteBodyRows();
                t.setSkipFirstHeader(true);
            }
        }
        doc.add(t);
    }

    // ----------------------------------------------------------------- helpers

    private static PdfPCell cell(String text, Font font, int align, Color background) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4f);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(RULE);
        if (background != null) {
            c.setBackgroundColor(background);
        }
        return c;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }

    private static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return f.format(v == null ? BigDecimal.ZERO : v);
    }

    /** Footer on every page: company and branch on the left, "Page x of y" on the right. */
    private static final class Footer extends PdfPageEventHelper {
        private final String company;
        private final String branch;
        private PdfTemplate total;
        private BaseFont base;

        Footer(String company, String branch) {
            this.company = company;
            this.branch = branch;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 22;
            cb.setColorStroke(RULE);
            cb.setLineWidth(0.6f);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();
            cb.setColorFill(MUTED);
            cb.beginText();
            cb.setFontAndSize(base, 7.5f);
            cb.setTextMatrix(document.left(), y);
            cb.showText(company + "  |  " + branch + "  |  Confidential");
            String page = "Page " + writer.getPageNumber() + " of ";
            float w = base.getWidthPoint(page, 7.5f);
            cb.setTextMatrix(document.right() - w - 14, y);
            cb.showText(page);
            cb.endText();
            cb.addTemplate(total, document.right() - 14, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, 7.5f);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
