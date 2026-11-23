package com.courier.modules.branchreport.application;

import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.shipment.domain.VendorAuditRow;
import com.courier.modules.finance.application.WalletService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders a {@link BranchStatement}: summary on page 1, transaction appendix after. */
public final class BranchStatementPdf {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH).withZone(IST);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private BranchStatementPdf() {
    }

    public static byte[] render(BranchStatement s, String companyName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            header(doc, s, companyName);
            summary(doc, s);
            appendix(doc, s);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render branch statement", e);
        }
        return out.toByteArray();
    }

    private static void header(Document doc, BranchStatement s, String companyName) throws DocumentException {
        doc.add(new Paragraph(companyName, TITLE));
        doc.add(new Paragraph("Branch: " + s.branchName() + " (" + s.branchCode() + ")", BODY));
        doc.add(new Paragraph("Period: " + DAY.format(s.from()) + " to " + DAY.format(s.to()), BODY));
        doc.add(new Paragraph("Generated on: " + DAY.format(LocalDate.now(IST)), BODY));
        doc.add(new Paragraph(" ", BODY));
    }

    private static void summary(Document doc, BranchStatement s) throws DocumentException {
        VendorAuditRow a = s.audit();
        WalletService.PeriodSummary w = s.wallet();
        PdfPTable t = new PdfPTable(new float[] {3f, 2f});
        t.setWidthPercentage(100);
        row(t, "Summary", "Amount", true);
        row(t, "Opening balance", money(w.openingBalance()), false);
        row(t, "Wallet recharges", w.rechargeCount() + " / " + money(w.rechargeAmount()), false);
        row(t, "Booking charges debited", money(w.bookingDebited()), false);
        row(t, "Booking commission", money(a.bookingTotalCommission()), false);
        row(t, "Delivery (DRS) commission credited", money(w.drsChargeCredited()), false);
        row(t, "Commission credited", money(w.commissionCredited()), false);
        row(t, "Commission debited", money(w.commissionDebited()), false);
        row(t, "ODA charges", money(a.odaCharges()), false);
        row(t, "Other charges", money(a.otherCharges()), false);
        row(t, "Paid orders (count / qty / amount)",
                a.paidOrderCount() + " / " + a.paidOrderQuantity() + " / " + money(a.paidOrderAmount()), false);
        row(t, "To-Pay orders (count / qty / amount)",
                a.topayOrderCount() + " / " + a.topayOrderQuantity() + " / " + money(a.topayOrderAmount()),
                false);
        row(t, "Orders booked", String.valueOf(a.totalBookedOrderCount()), false);
        row(t, "Orders delivered", String.valueOf(a.totalDeliveredOrderCount()), false);
        row(t, "Orders cancelled", String.valueOf(a.cancelledOrderCount()), false);
        row(t, "Closing balance", money(w.closingBalance()), true);
        doc.add(t);
    }

    private static void row(PdfPTable t, String label, String value, boolean bold) {
        t.addCell(cell(label, bold ? BOLD : BODY, Element.ALIGN_LEFT, bold));
        t.addCell(cell(value, bold ? BOLD : BODY, Element.ALIGN_RIGHT, bold));
    }

    private static void appendix(Document doc, BranchStatement s) throws DocumentException {
        doc.newPage();
        doc.add(new Paragraph("Transactions (" + s.transactions().size() + ")", TITLE));
        doc.add(new Paragraph(" ", SMALL));
        PdfPTable t = new PdfPTable(new float[] {2.2f, 2.4f, 2.6f, 2.6f, 1.6f, 1.6f, 1.8f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : new String[] {"Date (IST)", "Txn no", "Type", "Reference", "Debit", "Credit", "Balance"}) {
            t.addCell(cell(h, BOLD, h.equals("Debit") || h.equals("Credit") || h.equals("Balance")
                    ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, true));
        }
        for (WalletTransaction x : s.transactions()) {
            t.addCell(cell(x.getCreatedAt() == null ? "" : STAMP.format(x.getCreatedAt()), SMALL,
                    Element.ALIGN_LEFT, false));
            t.addCell(cell(nz(x.getTransactionNo()), SMALL, Element.ALIGN_LEFT, false));
            t.addCell(cell(x.getSubTransactionType() == null ? "" : x.getSubTransactionType().getLabel(),
                    SMALL, Element.ALIGN_LEFT, false));
            t.addCell(cell((x.getReferenceType() == null ? "" : x.getReferenceType().name())
                    + (x.getReferenceId() == null ? "" : " " + x.getReferenceId()), SMALL,
                    Element.ALIGN_LEFT, false));
            t.addCell(cell(x.isDebit() ? money(x.getAmount()) : "", SMALL, Element.ALIGN_RIGHT, false));
            t.addCell(cell(x.isCredit() ? money(x.getAmount()) : "", SMALL, Element.ALIGN_RIGHT, false));
            t.addCell(cell(money(x.getBalanceAfter()), SMALL, Element.ALIGN_RIGHT, false));
            // Flush completed rows to the writer so a big ledger is not held as one page-less table.
            if (t.getRows().size() >= 200) {
                doc.add(t);
                t.deleteBodyRows();
                t.setSkipFirstHeader(true);
            }
        }
        doc.add(t);
    }

    private static PdfPCell cell(String text, Font font, int align, boolean shaded) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(3f);
        c.setBorder(Rectangle.BOTTOM);
        if (shaded) {
            c.setBackgroundColor(new java.awt.Color(0xEE, 0xEE, 0xEE));
        }
        return c;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return f.format(v == null ? BigDecimal.ZERO : v);
    }
}
