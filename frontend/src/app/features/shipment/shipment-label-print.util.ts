import { barcodeSvg, qrSvg } from './consignment-print.util';

/** Everything one package label needs. Built once per shipment; the same data renders
 *  `numberOfPackages` labels — there is never more than one shipment/AWB behind them. */
export interface ShipmentLabelData {
  companyName: string;
  companyLogo: string | null;
  trackingNumber: string;
  bookingDate: string;
  bookingBranchLabel: string;
  /** Destination city/branch label. */
  destinationLabel: string;
  destinationPincode: string | null;
  senderName: string;
  receiverName: string;
  receiverContact: string;
  receiverAddress: string;
  weight: number;
  paymentModeLabel: string;
  serviceTypeLabel: string;
  /** Free-form routing line, e.g. "Pune → Hub Nagpur → Raipur". Null when unknown. */
  routing: string | null;
  numberOfPackages: number;
}

const esc = (s: string): string =>
  s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));

/** Package-level identifier, e.g. `AWB10001-002` — what the barcode/QR on each label encodes.
 *  Starts with the AWB so a scan resolves the shipment; the suffix says which package. */
export function packageId(awb: string, index: number): string {
  return `${awb}-${String(index).padStart(3, '0')}`;
}

/** Parses a scanned package id back into AWB + package number; null when it is a plain AWB. */
export function parsePackageId(scanned: string): { awb: string; packageNo: number } | null {
  const m = /^(.+)-(\d{3})$/.exec(scanned.trim());
  return m ? { awb: m[1], packageNo: parseInt(m[2], 10) } : null;
}

function label(d: ShipmentLabelData, n: number): string {
  const pkgId = packageId(d.trackingNumber, n);
  const weight = d.weight % 1 === 0 ? d.weight.toFixed(0) : d.weight.toFixed(3);
  return `
  <section class="label">
    <div class="row top">
      <div class="brand">${d.companyLogo
        ? `<img src="${esc(d.companyLogo)}" alt="${esc(d.companyName)}">`
        : `<span>${esc(d.companyName)}</span>`}</div>
      <div class="svc">${esc(d.serviceTypeLabel)}</div>
    </div>
    <div class="awb">
      <div class="k">AWB</div>
      <div class="awb-no">${esc(d.trackingNumber)}</div>
    </div>
    <div class="codes">
      <div class="bar">${barcodeSvg(pkgId)}<div class="pid">${esc(pkgId)}</div></div>
      <div class="qr">${qrSvg(pkgId)}</div>
    </div>
    <div class="dest">
      <div class="dest-main">
        <div class="k">DESTINATION</div>
        <div class="dest-city">${esc(d.destinationLabel)}</div>
        <div class="dest-pin">${d.destinationPincode ? esc(d.destinationPincode) : '—'}</div>
      </div>
      <div class="pkg"><div class="k">PKG</div><div class="pkg-no">${n}/${d.numberOfPackages}</div></div>
    </div>
    <table>
      <tr><td class="k">To</td><td colspan="3"><b>${esc(d.receiverName)}</b> &nbsp; ${esc(d.receiverContact)}</td></tr>
      <tr><td class="k">Address</td><td colspan="3" class="addr">${esc(d.receiverAddress)}</td></tr>
      <tr><td class="k">From</td><td>${esc(d.senderName)}</td><td class="k">Origin</td><td>${esc(d.bookingBranchLabel)}</td></tr>
      <tr><td class="k">Date</td><td>${esc(d.bookingDate)}</td><td class="k">Weight</td><td>${esc(weight)} kg</td></tr>
      <tr><td class="k">Payment</td><td colspan="3"><b>${esc(d.paymentModeLabel)}</b></td></tr>
      ${d.routing ? `<tr><td class="k">Route</td><td colspan="3">${esc(d.routing)}</td></tr>` : ''}
    </table>
  </section>`;
}

/** 4x6in (100x150mm) thermal/laser label CSS — black on white, no graphics. Shared by the
 *  on-screen preview and the print document so what you preview is what prints. */
const LABEL_CSS = `
  *{box-sizing:border-box}
  html,body{margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;color:#000;background:#fff}
  .label{width:100mm;height:150mm;border:2px solid #000;padding:2mm;display:flex;flex-direction:column;gap:1.5mm;overflow:hidden;page-break-after:always;break-after:page;margin:0 auto}
  .label:last-child{page-break-after:auto;break-after:auto}
  .k{font-size:7pt;font-weight:700;letter-spacing:.5px}
  .row.top{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #000;padding-bottom:1mm}
  .brand{font-size:12pt;font-weight:800}
  .brand img{max-height:11mm;max-width:50mm;object-fit:contain}
  .svc{font-size:9pt;font-weight:700;border:1.5px solid #000;padding:0 2mm}
  .awb{border-bottom:2px solid #000;padding-bottom:1mm}
  .awb-no{font-size:22pt;font-weight:900;letter-spacing:1px;line-height:1.1}
  .codes{display:flex;align-items:center;justify-content:space-between;gap:2mm;border-bottom:2px solid #000;padding-bottom:1.5mm}
  .bar{flex:1;text-align:center}
  .bar svg{width:100%;height:18mm}
  .pid{font-size:9pt;font-weight:700;letter-spacing:1px}
  .qr svg{width:24mm;height:24mm}
  .dest{display:flex;justify-content:space-between;align-items:stretch;border-bottom:2px solid #000;padding-bottom:1.5mm}
  .dest-main{flex:1;min-width:0}
  .dest-city{font-size:20pt;font-weight:900;line-height:1.1;text-transform:uppercase;overflow-wrap:anywhere}
  .dest-pin{font-size:20pt;font-weight:900;letter-spacing:2px}
  .pkg{border:3px solid #000;padding:1mm 3mm;text-align:center;display:flex;flex-direction:column;justify-content:center}
  .pkg-no{font-size:26pt;font-weight:900;line-height:1}
  table{width:100%;border-collapse:collapse}
  td{border:1px solid #000;padding:.8mm 1.5mm;font-size:8.5pt;vertical-align:top}
  td.k{width:14mm;background:#fff;font-size:7pt}
  td.addr{font-size:8pt}
`;

/** Full document for the given 1-based package numbers (all of them when omitted). Pure
 *  string so it can be previewed in an iframe or printed without touching the app DOM. */
export function renderLabelsHtml(d: ShipmentLabelData, packages?: number[], autoPrint = false): string {
  const nums = packages?.length ? packages : Array.from({ length: Math.max(1, d.numberOfPackages) }, (_, i) => i + 1);
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(d.trackingNumber)} labels</title>
<style>
${LABEL_CSS}
  @media screen{body{background:#e9e9e9;padding:8px}.label{background:#fff;margin:0 auto 10px}}
  @page{size:100mm 150mm;margin:0}
</style></head><body>
  ${nums.map((n) => label(d, n)).join('')}
  ${autoPrint ? '<script>window.onload = () => setTimeout(() => window.print(), 50);</script>' : ''}
</body></html>`;
}

/** Prints via a hidden iframe (same approach as `printConsignmentCopies`) so no app UI,
 *  sidebar or browser chrome is in the print job; `@page margin:0` drops headers/footers. */
export function printLabels(d: ShipmentLabelData, packages?: number[]): void {
  const iframe = document.createElement('iframe');
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
  document.body.appendChild(iframe);
  const cleanup = () => { if (iframe.parentNode) iframe.parentNode.removeChild(iframe); };
  iframe.contentWindow?.addEventListener('afterprint', cleanup);
  setTimeout(cleanup, 15_000);
  const doc = iframe.contentWindow?.document;
  if (!doc) { cleanup(); return; }
  doc.open();
  doc.write(renderLabelsHtml(d, packages, true));
  doc.close();
}
