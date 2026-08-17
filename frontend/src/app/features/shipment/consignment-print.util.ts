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
  const total = d.charges.netAmount + d.otherCharges;
  const detailRows: Array<[string, string]> = [
    ['From', d.bookingBranchLabel],
    ['To', d.deliveryBranchLabel],
    ['Type', `${d.serviceTypeLabel} - ${d.paymentModeLabel}`],
    ['No Of Parcel / Weight', `${d.numberOfPackages} / ${weight}`],
    ['Booking Date', d.bookingDate],
    ['Delivery Date', d.expectedDeliveryDate ?? '—'],
    ['Booking LR No', d.trackingNumber],
    ['Goods contain', d.packageTypeLabel]
  ];
  const chargeRows: Array<[string, number]> = [
    ['Booking Side Total', total],
    ['Unloading Delivery Charges', 0],
    ['GST On Hamali', 0],
    ['Demurrage Charge', 0],
    ['Reschedule Fine', 0]
  ];

  return `
    <section class="copy">
     <div class="receipt-inner">

      <!-- HEADER -->
      <div class="head">
        <div class="brand">
          <div class="logo">
            <div class="word">${esc(d.companyName)}</div>
            <svg class="swoosh" width="140" height="10" viewBox="0 0 150 12" aria-hidden="true">
              <path d="M2 9 Q75 -4 148 6" fill="none" stroke="#f7941d" stroke-width="3" stroke-linecap="round"/>
            </svg>
            <div class="tag">Courier &amp; Logistics</div>
          </div>
        </div>
        <div class="co">
          <h2>${esc(d.bookingBranchLabel)}</h2>
          <p>Booking Branch</p>
        </div>
        <div class="co">
          <h2>${esc(d.deliveryBranchLabel)}</h2>
          <p>Delivery Branch</p>
        </div>
        <div class="lrbox">
          <span class="lrbox-label">LR No</span>
          <span class="lrbox-no">${esc(d.trackingNumber)}</span>
        </div>
      </div>

      <!-- TITLE STRIP -->
      <div class="title">
        <div class="route">
          ${esc(d.bookingBranchLabel)} to ${esc(d.deliveryBranchLabel)}<br>
          (${esc(d.bookingDate)})
        </div>
        <div class="stamp">
          <div class="copy-label">${label}</div>
          <div class="ship-no">${esc(d.shipmentNumber)}</div>
        </div>
      </div>

      <!-- PARTY BLOCK -->
      <table class="party">
        <tr>
          <td class="lbl">Receiver Name :&nbsp; ${esc(d.receiverName)}</td>
          <td class="lbl">Receiver Mobile :&nbsp;&nbsp; ${esc(d.receiverContact)}</td>
          <td rowspan="2" class="tax">Tax Invoice : Consignment Note</td>
        </tr>
        <tr>
          <td class="lbl">Consignor Name :&nbsp; ${esc(d.senderName)}</td>
          <td class="lbl">Consignor Mobile :&nbsp; ${esc(d.senderContact)}</td>
        </tr>
      </table>

      <!-- MAIN BODY -->
      <div class="body">
        <div class="left">
          <table class="details">
            ${detailRows.map(([k, v]) => `<tr><td class="lbl">${esc(k)} :</td><td>${esc(v)}</td></tr>`).join('')}
          </table>
          <table class="small">
            <tr><td colspan="2">Special Instruction :&nbsp; ${esc(d.remarks ?? '—')}</td></tr>
            <tr><td colspan="2">Consignee GST Number :&nbsp; —</td></tr>
            <tr><td colspan="2">Delivery Type :&nbsp; Door Delivery</td></tr>
          </table>
        </div>
        <div class="right">
          <table class="charges">
            <tr><th style="text-align:left">Description</th><th style="text-align:right">Amount(Rs.)</th></tr>
            ${chargeRows.map(([k, v]) => `<tr><td>${esc(k)}</td><td>${v.toFixed(2)}</td></tr>`).join('')}
            <tr class="total"><td>Total Receivable</td><td>${total.toFixed(2)}</td></tr>
          </table>
          <div class="zero">${esc(amountInWords(total))}</div>
          <div class="note">
            Note : Terms And Conditions Applied<br>
            All articles are received in good condition
          </div>
        </div>
      </div>

      <!-- FOOTER -->
      <div class="footer">
        <div class="gen">* This is a computer-generated receipt, printed ${esc(new Date().toLocaleString('en-IN'))}. No signature or stamp is required.</div>
        <div class="sign">
          <span>Representative Signature</span>
          <span>Customer Signature</span>
        </div>
      </div>

     </div>
    </section>`;
}

/**
 * Builds the full printable document (both copies) as a plain HTML string — pulled out of
 * `printConsignmentCopies` so it can be rendered and inspected (e.g. for a visual check
 * against real booking data) without touching the DOM or triggering `window.print()`.
 */
export function renderConsignmentHtml(data: ConsignmentPrintData, autoPrint = true): string {
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(data.shipmentNumber)}</title>
<style>
  :root{ --ink:#000; --line:#000; --orange:#f7941d; --muted:#333; }
  *{box-sizing:border-box}
  html,body{margin:0;padding:0}
  body{font-family:"Segoe UI",Calibri,Arial,Helvetica,sans-serif;color:var(--ink);background:#e9e9e9}

  .copy{width:1100px;max-width:100%;margin:0 auto;background:#fff;border:3px solid var(--line);padding:10px;page-break-after:always}
  .copy:last-child{page-break-after:auto}
  .receipt-inner{border:2px solid var(--line)}

  /* header */
  .head{display:grid;grid-template-columns:220px 1fr 1fr 180px;border-bottom:2px solid var(--line)}
  .head > div{padding:10px 12px}
  .head .brand{display:flex;align-items:center;justify-content:center}
  .logo{line-height:1;text-align:center}
  .logo .word{font-size:22px;font-weight:800;letter-spacing:-.3px}
  .logo .swoosh{display:block;margin:2px auto 0}
  .logo .tag{font-size:10px;color:var(--orange);font-weight:600;margin-top:2px}
  .co{font-size:13px;line-height:1.3;border-left:2px solid var(--line)}
  .co h2{margin:0 0 2px;font-size:15px;font-weight:700;text-align:center}
  .co p{margin:0;color:var(--muted);text-align:center}
  .lrbox{border-left:2px solid var(--line);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
  .lrbox-label{font-size:11px;font-weight:700;color:var(--muted)}
  .lrbox-no{font-size:18px;font-weight:800}

  /* title strip */
  .title{display:grid;grid-template-columns:1fr auto;align-items:start;gap:16px;padding:8px 14px 10px;border-bottom:2px solid var(--line)}
  .title .route{font-size:20px;font-weight:700;line-height:1.25}
  .title .stamp{text-align:right;line-height:1.25}
  .title .copy-label{font-size:16px;font-weight:700}
  .title .ship-no{font-size:16px;font-weight:700}

  /* shared table look */
  table{width:100%;border-collapse:collapse}
  td,th{border:1px solid var(--line);padding:5px 8px;font-size:12px;vertical-align:middle}
  .lbl{font-weight:700;white-space:nowrap}

  .party{border-top:0}
  .party td{height:26px}
  .party td.tax{width:26%;font-weight:700;text-align:center;vertical-align:middle}

  /* body grid */
  .body{display:grid;grid-template-columns:1fr 1fr}
  .body .right{border-left:0}
  .details td:first-child{width:42%}
  .charges td:last-child{text-align:right;width:32%}
  .charges .total td{font-size:15px;font-weight:700}
  .zero{border:1px solid var(--line);border-top:0;text-align:right;padding:6px 8px;font-size:14px;font-weight:700}
  .note{border:1px solid var(--line);border-top:0;padding:8px;font-size:11px;font-weight:700;line-height:1.5}
  .small td{font-size:11px;padding:3px 8px}

  /* footer */
  .footer{padding:6px 10px 10px;font-size:11px;line-height:1.4}
  .footer .gen{font-weight:700}
  .sign{display:flex;justify-content:space-between;font-weight:700;font-size:12px;margin-top:20px}

  @media print{
    body{background:#fff}
    .copy{border:2px solid #000;width:100%;padding:6px}
    @page{size:A4 landscape;margin:8mm}
  }
</style></head><body>
  ${copy(data, 'Original Copy')}
  ${copy(data, 'Office Copy')}
  ${autoPrint ? '<script>window.onload = () => setTimeout(() => window.print(), 50);</script>' : ''}
</body></html>`;
}

/**
 * Opens a hidden iframe (not a new tab/window — avoids popup-blocker issues when called
 * from an async success callback rather than directly from a click) holding both copies,
 * one per printed page, and triggers the browser print dialog once it has rendered. The
 * iframe is removed shortly after `afterprint` fires (or a fallback timeout, since some
 * browsers don't fire it for iframe-hosted print jobs).
 */
export function printConsignmentCopies(data: ConsignmentPrintData): void {
  const html = renderConsignmentHtml(data);

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
