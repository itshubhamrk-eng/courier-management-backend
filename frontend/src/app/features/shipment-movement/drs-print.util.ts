import { PRINT_HEADER_CSS, renderPrintHeader } from '@features/shipment/consignment-print.util';

export interface DrsPrintRow {
  shipmentNumber: string;
  ewayBillNumber?: string | null;
  receiverName: string;
  receiverContact: string;
  fromCity?: string | null;
  toCity?: string | null;
  paymentLabel: string;
  /** Amount to collect at delivery; null prints a dash (PAID / TBB rows). */
  collectAmount: number | null;
}

export interface DrsPrintOptions {
  rows: DrsPrintRow[];
  drsNumber: string;
  branchLabel: string;
  deliveryUserLabel: string;
  dateLabel: string;
  companyName: string;
  companyLogo: string | null;
  companyAddress: string | null;
  companyGst: string | null;
  companyContact: string | null;
}

function esc(s: string | null | undefined): string {
  return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** The DRS print sheet's full HTML — shared by Out For Delivery's Print DRS and the DRS
 *  Report's Reprint, so a reprint is identical to the original. */
export function renderDrsHtml(o: DrsPrintOptions): string {
  const { companyName, companyLogo, companyAddress, companyGst, companyContact, drsNumber: drsNo } = o;
  const totalAmount = o.rows.reduce((sum, r) => sum + (r.collectAmount ?? 0), 0);
  const tableRows = o.rows.map((s, i) => `<tr>
      <td class="center">${i + 1}</td>
      <td>${esc(s.shipmentNumber)}</td>
      <td>${esc(s.ewayBillNumber) || '—'}</td>
      <td>${esc(s.receiverName)}</td>
      <td>${esc(s.receiverContact)}</td>
      <td>${esc(s.fromCity) || '—'}</td>
      <td>${esc(s.toCity) || '—'}</td>
      <td>${esc(s.paymentLabel)}</td>
      <td class="right">${s.collectAmount ?? '—'}</td>
      <td class="c-sign"></td>
      <td class="c-stamp"></td>
    </tr>`).join('');
  return `<!doctype html><html><head><meta charset="utf-8"><title>DRS ${esc(o.deliveryUserLabel)}</title>
      <style>
        * { box-sizing: border-box; }
        body { margin: 0; padding: 20px; background: #f3f3f3; font-family: Arial, Helvetica, sans-serif; color: #111; font-size: 12px; }
        .toolbar { width: 900px; margin: 0 auto 10px; }
        button { padding: 5px 12px; margin-right: 5px; border: 1px solid #000; background: #eee; cursor: pointer; font-size: 15px; }
        .sheet { width: 900px; margin: auto; background: #fff; border: 1px solid #000; }
        .title { text-align: center; font-size: 18px; font-weight: bold; padding: 4px 0; border-bottom: 1px solid #000; }
        ${PRINT_HEADER_CSS}
        .head, .head .co, .head .lrbox { border-color: #000; border-width: 1px; }
        .meta { display: grid; grid-template-columns: repeat(4, 1fr); border-bottom: 1px solid #000; }
        .meta div { padding: 4px 6px; border-right: 1px solid #000; }
        .meta div:nth-child(4n) { border-right: 0; }
        .label { font-weight: bold; display: block; }
        table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        th, td { border-right: 1px solid #000; border-bottom: 1px solid #000; padding: 3px 4px; vertical-align: middle; min-height: 19px; word-wrap: break-word; overflow-wrap: break-word; }
        th:last-child, td:last-child { border-right: 0; }
        th { font-weight: bold; text-align: center; background: #fafafa; font-size: 11px; }
        td { font-size: 11px; }
        td.center, th.center { text-align: center; }
        td.right, th.right { text-align: right; }
        .c-sr { width: 26px; }
        .c-amount { width: 60px; }
        .c-sign, .c-stamp { width: 100px; }
        td.c-sign, td.c-stamp { min-height: 34px; height: 34px; }
        .total-row td { font-weight: bold; min-height: 22px; }
        .footer { display: grid; grid-template-columns: 1fr 150px; min-height: 38px; }
        .footer-left { padding: 5px; border-right: 1px solid #000; }
        .footer-right { text-align: center; padding: 5px; font-weight: bold; }
        .signature { height: 22px; margin-top: 2px; }
        @media print {
          body { background: #fff; padding: 0; margin: 0; }
          .toolbar { display: none; }
          .sheet { width: 100%; }
          @page { size: A4 portrait; margin: 8mm; }
        }
        @media screen and (max-width: 950px) { .sheet, .toolbar { width: 100%; overflow-x: auto; } }
      </style></head><body>

      <div class="toolbar">
        <button onclick="window.print()">Print</button>
        <button onclick="window.print()">Download PDF</button>
      </div>

      <div class="sheet">
        <div class="title">DELIVERY RUN SHEET (DRS)</div>

        ${renderPrintHeader(
          { companyName, companyLogo, companyAddress, companyGst, companyContact, companyWebsite: null },
          { label: 'DRS No', value: drsNo, barcodeValue: drsNo !== '—' ? drsNo : undefined, qrValue: drsNo !== '—' ? drsNo : undefined }
        )}

        <div class="meta">
          <div><span class="label">BRANCH</span>${esc(o.branchLabel)}</div>
          <div><span class="label">DELIVERY BOY</span>${esc(o.deliveryUserLabel)}</div>
          <div><span class="label">DATE</span>${esc(o.dateLabel)}</div>
          <div><span class="label">SHIPMENTS</span>${o.rows.length}</div>
        </div>

        <table>
          <thead><tr>
            <th class="c-sr">SR<br>NO.</th>
            <th>SHIPMENT NO.</th>
            <th>E-WAY BILL NO.</th>
            <th>RECEIVER</th>
            <th>CONTACT</th>
            <th>FROM CITY</th>
            <th>TO CITY</th>
            <th>PAYMENT</th>
            <th class="c-amount right">AMOUNT</th>
            <th class="c-sign">RECEIVER SIGN</th>
            <th class="c-stamp">STAMP</th>
          </tr></thead>
          <tbody>${tableRows || '<tr><td colspan="11" class="center">No shipments</td></tr>'}</tbody>
          <tfoot><tr class="total-row">
            <td colspan="8" class="right">Total to Collect</td>
            <td class="right">${totalAmount}</td>
            <td></td>
            <td></td>
          </tr></tfoot>
        </table>

        <div class="footer">
          <div class="footer-left"><strong>${esc(companyName)}</strong></div>
          <div class="footer-right">SIGNATURE<div class="signature"></div></div>
        </div>
      </div>
    </body></html>`;
}
