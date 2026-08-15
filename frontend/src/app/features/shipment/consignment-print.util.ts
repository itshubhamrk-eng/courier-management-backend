import { ChargeBreakup } from '@core/models/shipment.model';

/** Everything the consignment note needs — nothing invented, every value comes off the
 *  real booking form and the price it was actually booked at (same `PricingResponse` the
 *  live preview showed, since the server prices what it books). Laid out to match a real
 *  courier LR template (SmartPost-style) row for row — see `copy()` below for which of its
 *  fields (Parcel Received, Consignee GST, Delivery Type) this system doesn't track and
 *  prints as "—". */
export interface ConsignmentPrintData {
  companyName: string;
  shipmentNumber: string;
  trackingNumber: string;
  bookingDate: string;
  /** Backend only returns this once pricing/route resolves a transit time — absent on some bookings. */
  expectedDeliveryDate: string | null;
  bookingBranchLabel: string;
  deliveryBranchLabel: string;
  senderName: string;
  senderAddress: string;
  senderContact: string;
  receiverName: string;
  receiverAddress: string;
  receiverContact: string;
  serviceTypeLabel: string;
  packageTypeLabel: string;
  paymentModeLabel: string;
  numberOfPackages: number;
  chargeableWeight: number;
  declaredValue: number | null;
  charges: ChargeBreakup;
  /** Manual, typed at booking time — not part of the Pricing Engine's own `ChargeBreakup`. */
  otherCharges: number;
  /** Booking form's remarks field — printed as the LR's "Special Instruction" line. */
  remarks: string | null;
}

const esc = (s: string): string =>
  s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));

const ONES = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
  'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

function twoDigitWords(n: number): string {
  if (n < 20) return ONES[n];
  return TENS[Math.floor(n / 10)] + (n % 10 ? ' ' + ONES[n % 10] : '');
}
function threeDigitWords(n: number): string {
  const hundred = Math.floor(n / 100);
  const rest = n % 100;
  return [hundred ? `${ONES[hundred]} Hundred` : '', rest ? twoDigitWords(rest) : ''].filter(Boolean).join(' ');
}
/** Indian numbering (Lakh/Crore) — this LR template prints the payable amount in words
 *  the way physical courier receipts traditionally do, e.g. "Two Hundred Fifteen Only". */
function amountInWords(amount: number): string {
  let n = Math.round(amount);
  if (n === 0) return 'Zero Only';
  const crore = Math.floor(n / 1e7); n %= 1e7;
  const lakh = Math.floor(n / 1e5); n %= 1e5;
  const thousand = Math.floor(n / 1e3); n %= 1e3;
  const parts = [
    crore ? `${threeDigitWords(crore)} Crore` : '',
    lakh ? `${threeDigitWords(lakh)} Lakh` : '',
    thousand ? `${threeDigitWords(thousand)} Thousand` : '',
    n ? threeDigitWords(n) : ''
  ].filter(Boolean);
  return `${parts.join(' ')} Only`;
}

function copy(d: ConsignmentPrintData, label: 'Original Copy' | 'Customer Copy' | 'Office Copy'): string {
  const weight = d.chargeableWeight % 1 === 0 ? d.chargeableWeight.toFixed(0) : d.chargeableWeight.toFixed(3);
  const detailRows: Array<[string, string]> = [
    ['From', d.bookingBranchLabel],
    ['To', d.deliveryBranchLabel],
    ['Type', `${d.serviceTypeLabel} - ${d.paymentModeLabel}`],
    ['No Of Parcel / Weight', `${d.numberOfPackages} / ${weight}`],
    ['Parcel Received', '—'],
    ['Delivery Date', d.expectedDeliveryDate ?? '—'],
    ['Booking Date', d.bookingDate],
    ['Booking LR No', d.trackingNumber],
    ['Goods contain', d.packageTypeLabel],
    ['Special Instruction', d.remarks ?? '—'],
    ['Consignee GST Number', '—'],
    ['Delivery Type', '—']
  ];
  const total = d.charges.netAmount + d.otherCharges;
  const chargeRows: Array<[string, number]> = [
    ['Booking Side Total', total],
    ['Unloading Delivery Charges', 0],
    ['GST On Hamali', 0],
    ['Demurrage Charge', 0],
    ['Reschedule Fine', 0]
  ];

  return `
    <section class="copy">
      <header class="head">
        <h1>${esc(d.companyName)}</h1>
        <span class="badge">${label}</span>
      </header>
      <div class="banner">
        <span>${esc(d.bookingBranchLabel)} to ${esc(d.deliveryBranchLabel)} (${esc(d.bookingDate)})</span>
        <strong>${esc(d.trackingNumber)}</strong>
      </div>
      <table class="strip">
        <tr>
          <td class="k">Receiver Name:</td><td class="v">${esc(d.receiverName)}</td>
          <td class="k">Receiver Mobile:</td><td class="v">${esc(d.receiverContact)}</td>
          <td class="tax" rowspan="2">Tax Invoice: Consignment Note</td>
        </tr>
        <tr>
          <td class="k">Consignor Name:</td><td class="v">${esc(d.senderName)}</td>
          <td class="k">Consignor Mobile:</td><td class="v">${esc(d.senderContact)}</td>
        </tr>
      </table>
      <div class="grid">
        <table class="kv">${detailRows.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${esc(v)}</td></tr>`).join('')}</table>
        <table class="kv charges">
          <tr><td class="k head-row">Description</td><td class="v head-row">Amount(Rs.)</td></tr>
          ${chargeRows.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${v.toFixed(2)}</td></tr>`).join('')}
          <tr class="total"><td class="k">Total Receivable</td><td class="v">${total.toFixed(2)}</td></tr>
          <tr class="words"><td colspan="2">${esc(amountInWords(total))}</td></tr>
        </table>
      </div>
      <div class="note">
        Note: Terms And Conditions Applied. All articles are received in good condition.
      </div>
      <div class="sign">
        <span>Representative Signature</span>
        <span>Customer Signature</span>
      </div>
      <div class="foot">* This is a computer-generated receipt, printed ${esc(new Date().toLocaleString('en-IN'))}. No signature or stamp is required.</div>
    </section>`;
}

/**
 * Opens a hidden iframe (not a new tab/window — avoids popup-blocker issues when called
 * from an async success callback rather than directly from a click) holding both copies,
 * one per printed page, and triggers the browser print dialog once it has rendered. The
 * iframe is removed shortly after `afterprint` fires (or a fallback timeout, since some
 * browsers don't fire it for iframe-hosted print jobs).
 */
export function printConsignmentCopies(data: ConsignmentPrintData): void {
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${esc(data.shipmentNumber)}</title>
<style>
  *{box-sizing:border-box} body{font:12px/1.4 -apple-system,Segoe UI,Roboto,sans-serif;color:#0f172a;margin:0}
  .copy{padding:20px;page-break-after:always;border:1px solid #0f172a}
  .copy:last-child{page-break-after:auto}
  .head{display:flex;align-items:center;justify-content:space-between;border-bottom:2px solid #0f172a;padding:0 0 8px;margin-bottom:0}
  .head h1{margin:0;font-size:18px}
  .badge{font-weight:700;font-size:12px;letter-spacing:.04em;text-transform:uppercase;padding:4px 10px;border:1px solid #0f172a;border-radius:999px}
  .banner{display:flex;align-items:center;justify-content:space-between;border:1px solid #0f172a;border-top:none;padding:6px 8px;font-weight:700;font-size:13px}
  table.strip{width:100%;border-collapse:collapse;border:1px solid #0f172a;border-top:none}
  table.strip td{padding:5px 8px;border-bottom:1px solid #0f172a;font-size:12px}
  table.strip td.k{color:#475569;white-space:nowrap;width:1%}
  table.strip td.v{font-weight:600;padding-right:16px}
  table.strip td.tax{font-weight:700;text-align:center;vertical-align:middle;border-left:1px solid #0f172a;width:22%}
  .grid{display:grid;grid-template-columns:1.3fr 1fr;border:1px solid #0f172a;border-top:none}
  table.kv{width:100%;border-collapse:collapse}
  table.kv:first-child{border-right:1px solid #0f172a}
  table.kv td{padding:5px 8px;border-bottom:1px solid #0f172a;font-size:12px}
  table.kv td.k{color:#475569;width:56%} table.kv td.v{font-weight:600;text-align:right}
  table.charges td.head-row{font-weight:700;background:#f1f5f9;text-align:left}
  table.charges td.head-row.v{text-align:right}
  table.charges tr.total td{border-top:2px solid #0f172a;font-weight:800;font-size:14px}
  table.charges tr.words td{border-bottom:none;font-weight:700;text-align:right;font-size:11px}
  .note{border:1px solid #0f172a;border-top:none;padding:6px 8px;font-size:11px;font-weight:600}
  .sign{display:flex;justify-content:space-between;padding:24px 8px 4px;font-size:12px;font-weight:700}
  .foot{font-size:10px;color:#94a3b8;text-align:center;margin-top:6px}
  @media print{.copy{padding:14px}}
</style></head><body>
  ${copy(data, 'Original Copy')}
  ${copy(data, 'Office Copy')}
  <script>window.onload = () => setTimeout(() => window.print(), 50);</script>
</body></html>`;

  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = '0';
  document.body.appendChild(iframe);

  const cleanup = () => { if (iframe.parentNode) iframe.parentNode.removeChild(iframe); };
  iframe.contentWindow?.addEventListener('afterprint', cleanup);
  setTimeout(cleanup, 15_000);

  const doc = iframe.contentWindow?.document;
  if (!doc) { cleanup(); return; }
  doc.open();
  doc.write(html);
  doc.close();
}
