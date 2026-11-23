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
 * Renders a {@link BranchStatement} as a single branded page: header band with the company logo,
 * key figures, wallet account, wallet movements by transaction type, and orders/charges. The
 * transaction-by-transaction ledger is deliberately left out so the statement stays one page
 * however busy the month was.
 */
public final class BranchStatementPdf {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

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
        return render(s, companyName, null);
    }

    /** @param logo image bytes (PNG/JPEG/GIF) for the header, or null / unreadable for a name-only header */
    public static byte[] render(BranchStatement s, String companyName, byte[] logo) {
        byte[] pdf = renderOnce(s, companyName, logo, false);
        if (pageCount(pdf) > 1) {
            // Many transaction types: render again with tighter spacing so it stays one page.
            pdf = renderOnce(s, companyName, logo, true);
        }
        return pdf;
    }

    private static int pageCount(byte[] pdf) {
        try {
            return new com.lowagie.text.pdf.PdfReader(pdf).getNumberOfPages();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read back the rendered statement", e);
        }
    }

    /** Tighter paddings/gaps for the second attempt; confined to the rendering thread. */
    private static final ThreadLocal<Boolean> COMPACT = ThreadLocal.withInitial(() -> false);

    private static float pad(float normal) {
        return COMPACT.get() ? Math.max(1.5f, normal * 0.5f) : normal;
    }

    private static byte[] renderOnce(BranchStatement s, String companyName, byte[] logo, boolean compact) {
        COMPACT.set(compact);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, compact ? 24 : 32, 44);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(companyName, s.branchName()));
            doc.open();
            header(doc, s, companyName, logo);
            keyFigures(doc, s);
            walletSection(doc, s);
            ordersSection(doc, s);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render branch statement", e);
        } finally {
            COMPACT.remove();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ page 1

    private static void header(Document doc, BranchStatement s, String companyName, byte[] logo)
            throws DocumentException {
        com.lowagie.text.Image image = null;
        if (logo != null && logo.length > 0) {
            try {
                image = com.lowagie.text.Image.getInstance(logo);
                image.scaleToFit(62f, 46f);
            } catch (Exception e) {
                image = null; // unsupported format (SVG/WEBP) or corrupt: fall back to the name only
            }
        }
        PdfPTable band = image == null ? new PdfPTable(new float[] {3f, 2f}) : new PdfPTable(new float[] {1.1f, 3f, 2f});
        band.setWidthPercentage(100);
        if (image != null) {
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBackgroundColor(BRAND);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(10f);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            PdfPTable plate = new PdfPTable(1);
            PdfPCell inner = new PdfPCell(image, false);
            inner.setBackgroundColor(Color.WHITE);
            inner.setBorder(Rectangle.NO_BORDER);
            inner.setPadding(5f);
            inner.setHorizontalAlignment(Element.ALIGN_CENTER);
            inner.setVerticalAlignment(Element.ALIGN_MIDDLE);
            plate.addCell(inner);
            logoCell.addElement(plate);
            band.addCell(logoCell);
        }
        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(BRAND);
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(pad(14f));
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        left.addElement(new Paragraph(companyName, H_COMPANY));
        left.addElement(new Paragraph("BRANCH MONTHLY STATEMENT", H_SUB));
        band.addCell(left);
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(BRAND);
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(pad(14f));
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
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
        info.setSpacingBefore(pad(10f));
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
        c.setPadding(pad(8f));
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
        t.setSpacingBefore(pad(12f));
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
        c.setPadding(pad(9f));
        c.addElement(new Paragraph(label, LABEL));
        Paragraph v = new Paragraph(value, KPI);
        v.setSpacingBefore(2f);
        c.addElement(v);
        return c;
    }

    private static void section(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, SECTION);
        p.setSpacingBefore(pad(12f));
        p.setSpacingAfter(pad(5f));
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
        Paragraph note = new Paragraph("Delivery commission is the amount credited to the wallet. The full transaction-by-transaction ledger is available in the app.", NOTE);
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

    // ----------------------------------------------------------------- helpers

    private static PdfPCell cell(String text, Font font, int align, Color background) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(pad(4f));
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
