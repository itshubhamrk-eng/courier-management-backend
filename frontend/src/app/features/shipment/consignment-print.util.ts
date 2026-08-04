import { ChargeBreakup } from '@core/models/shipment.model';

/** Everything the consignment note needs — nothing invented, every value comes off the
 *  real booking form and the price it was actually booked at (same `PricingResponse` the
 *  live preview showed, since the server prices what it books). */
export interface ConsignmentPrintData {
  companyName: string;
  shipmentNumber: string;
  trackingNumber: string;
  bookingDate: string;
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
}

const esc = (s: string): string =>
  s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));

function copy(d: ConsignmentPrintData, label: 'Customer Copy' | 'Office Copy'): string {
  const rows: Array<[string, string]> = [
    ['Shipment No.', d.shipmentNumber],
    ['AWB / Tracking No.', d.trackingNumber],
    ['Booking Date', d.bookingDate],
    ['Route', `${d.bookingBranchLabel} → ${d.deliveryBranchLabel}`],
    ['Service Type', d.serviceTypeLabel],
    ['Package Type', d.packageTypeLabel],
    ['Payment Mode', d.paymentModeLabel],
    ['Packages', String(d.numberOfPackages)],
    ['Chargeable Weight', `${d.chargeableWeight.toFixed(3)} kg`],
    ['Declared Value', d.declaredValue != null ? d.declaredValue.toFixed(2) : '—']
  ];
  const chargeRows: Array<[string, number]> = [
    ['Freight', d.charges.freight],
    ['Fuel Surcharge', d.charges.fuelCharge],
    ['Handling', d.charges.handlingCharge],
    ['ODA', d.charges.odaCharge],
    ['Insurance', d.charges.insuranceCharge],
    ['GST', d.charges.gstAmount],
    ['Discount', -d.charges.discount],
    ['Round Off', d.charges.roundOff]
  ];

  return `
    <section class="copy">
      <header class="head">
        <h1>${esc(d.companyName)}</h1>
        <span class="badge">${label}</span>
      </header>
      <table class="kv">${rows.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${esc(v)}</td></tr>`).join('')}</table>
      <div class="parties">
        <div class="party">
          <div class="party__t">Consignor</div>
          <div class="party__n">${esc(d.senderName)}</div>
          <div>${esc(d.senderAddress)}</div>
          <div>${esc(d.senderContact)}</div>
        </div>
        <div class="party">
          <div class="party__t">Consignee</div>
          <div class="party__n">${esc(d.receiverName)}</div>
          <div>${esc(d.receiverAddress)}</div>
          <div>${esc(d.receiverContact)}</div>
        </div>
      </div>
      <table class="kv charges">${chargeRows.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${v.toFixed(2)}</td></tr>`).join('')}
        <tr class="total"><td class="k">Net Amount</td><td class="v">${d.charges.netAmount.toFixed(2)}</td></tr>
      </table>
      <div class="foot">Generated ${esc(new Date().toLocaleString('en-IN'))} — this is a system-generated consignment note.</div>
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
  *{box-sizing:border-box} body{font:13px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;color:#0f172a;margin:0}
  .copy{padding:28px;page-break-after:always}
  .copy:last-child{page-break-after:auto}
  .head{display:flex;align-items:center;justify-content:space-between;border-bottom:2px solid #0f172a;padding-bottom:10px;margin-bottom:14px}
  .head h1{margin:0;font-size:18px}
  .badge{font-weight:700;font-size:12px;letter-spacing:.04em;text-transform:uppercase;padding:4px 10px;border:1px solid #0f172a;border-radius:999px}
  table.kv{width:100%;border-collapse:collapse;margin-bottom:14px}
  table.kv td{padding:5px 4px;border-bottom:1px solid #e2e8f0;font-size:12px}
  table.kv td.k{color:#475569;width:45%} table.kv td.v{font-weight:600;text-align:right}
  table.charges tr.total td{border-top:2px solid #0f172a;border-bottom:none;font-weight:800;font-size:14px}
  .parties{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px}
  .party{border:1px solid #e2e8f0;border-radius:8px;padding:10px}
  .party__t{font:700 10px sans-serif;text-transform:uppercase;letter-spacing:.06em;color:#475569;margin-bottom:4px}
  .party__n{font-weight:700}
  .foot{font-size:10px;color:#94a3b8;text-align:center;margin-top:10px}
  @media print{.copy{padding:16px}}
</style></head><body>
  ${copy(data, 'Customer Copy')}
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
