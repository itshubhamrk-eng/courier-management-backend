import { isTbbLabel } from '@core/utils/tbb.util';
import { ConsignmentPrintData, qrSvg } from './consignment-print.util';

/** "Shipment Copy" — compact black-and-white LR matching the legacy Ambox consignment
 *  copy (Consignment/Date/From/Destination box, consignor/consignee/QR code row, charges
 *  column, special instructions). Three copies stack on one A4 portrait page separated by
 *  a dashed cut line. Takes an array so the same renderer serves single and bulk print. */

const esc = (s: string | null | undefined): string =>
  (s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));

const COPIES = ['CONSIGNOR COPY', 'DRIVER COPY', 'OFFICE COPY'];

const money = (v: number | null | undefined): string => (v ? v.toFixed(2) : '');
const num = (v: number): string => (v % 1 === 0 ? v.toFixed(2) : v.toFixed(2));

function copy(d: ConsignmentPrintData, label: string): string {
  const actual = d.items.length ? d.items.reduce((s, i) => s + i.weight, 0) : d.chargeableWeight;
  const dims = d.items.length === 1 ? d.items[0] : null;
  const volumetric = dims?.lengthCm && dims?.widthCm && dims?.heightCm
    ? (dims.lengthCm * dims.widthCm * dims.heightCm) / 5000 : 0;

  const mathadi = d.otherCharges + (d.appointmentDeliveryCharge ?? 0) + (d.doorDeliveryCharge ?? 0) + (d.toPayCharge ?? 0);
  const fov = d.charges.insuranceCharge + d.charges.fuelCharge + d.charges.applicableCharges;
  const total = d.charges.netAmount + mathadi;
  const gst = d.charges.gstAmount;
  const subtotal = total - gst;

  const mode = d.paymentModeLabel.toUpperCase();
  const isPaid = mode.includes('PAID') && !mode.includes('TO_PAY');
  const isToPay = mode.includes('TO_PAY') || mode.includes('TO PAY');
  // To Be Billed (TBB) is the credit account — billed to the customer later.
  const isCredit = mode.includes('CREDIT') || mode.includes('TBB');
  const tick = (on: boolean) => (on ? '&#9745;' : '&#9744;');

  const isTbb = isTbbLabel(d.paymentModeLabel);
  const charges: Array<[string, string]> = isTbb ? [
    ['Freight', ''], ['Docket Ch', ''], ['DOD/COD Ch', ''], ['ODA Ch', ''], ['Mathadi Ch.', ''], ['Packaging Ch', ''],
    ['Total Charges', ''], ['FOV', ''], ['GST', ''], ['Grand Total', 'Credit Customer']
  ] : [
    ['Freight', money(d.charges.freight)],
    ['Docket Ch', money(d.charges.handlingCharge)],
    ['DOD/COD Ch', ''],
    ['ODA Ch', money(d.charges.odaCharge)],
    ['Mathadi Ch.', money(mathadi)],
    ['Packaging Ch', ''],
    ['Total Charges', money(subtotal)],
    ['FOV', money(fov)],
    ['GST', money(gst)],
    ['Grand Total', money(total)]
  ];

  const dest = d.deliveryBranchLabel || '—';
  return `
  <div class="slot"><div class="lr">
    <div class="top">
      <div class="logo">${d.companyLogo ? `<img src="${esc(d.companyLogo)}" alt="">` : `<b>${esc(d.companyName)}</b>`}</div>
      <div class="addr">${esc(d.companyAddress)}${d.companyContact ? ` &nbsp; Mobile : ${esc(d.companyContact)}` : ''}${d.companyWebsite ? ` &nbsp; ${esc(d.companyWebsite)}` : ''}
        ${d.companyGst ? `<div>GST NO : ${esc(d.companyGst)}</div>` : ''}</div>
      <div class="meta">
        <div class="mr"><span class="mk">CONSIGNMENT</span><span class="mv big">${esc(d.shipmentNumber)}</span><span class="mk">DATE</span><span class="mv big">${esc(d.bookingDate)}</span></div>
        <div class="mr"><span class="mk">FROM</span><span class="mv big">${esc(d.bookingBranchLabel)}</span><span class="mk">DESTINATION</span><span class="mv big">${esc(dest)}</span></div>
      </div>
    </div>
    <div class="party">
      <div class="p1"><div class="h">CONSIGNOR</div><div class="b">${esc(d.senderName)}</div></div>
      <div class="p2"><div class="h">CONSIGNEE</div><div class="b">${esc(d.receiverName)}</div></div>
      <div class="p3"><div class="h">QR CODE</div><div class="bc">${qrSvg(d.trackingNumber)}</div></div>
    </div>
    <div class="grid">
      <div class="g-left">
        <div class="wt">
          <div><div class="h">No. of Piece</div><div class="c">${d.numberOfPackages}</div></div>
          <div><div class="h">Actual Weight</div><div class="c">${num(actual)}</div></div>
          <div><div class="h">Chargeable Weight</div><div class="c">${num(d.chargeableWeight)}</div></div>
        </div>
        <div class="wt2">
          <div><div class="h">Invoice Value</div><div class="c s">${(d.invoiceValue ?? 0).toFixed(2)}</div></div>
          <div><div class="h">Invoice No</div><div class="c s">0</div></div>
          <div><div class="h">Vehicle No</div><div class="c s"></div></div>
        </div>
        <div class="risk"><b>AT OWNER'S RISK / CARRIER'S RISK</b> If Insured details of Insurance Policy<br>
          Policy No : &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Date: &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Insurance Company :<br>
          Insurance Value : ${(d.declaredValue ?? 0).toFixed(2)}<br>
          Invoice &#9744; Y &#9744; N &nbsp; Packing List &#9744; Y &#9744; N &nbsp; Permit / Form &#9744; Y &#9744; N</div>
        <div class="terms">I/We hereby agree to the terms &amp; conditions setout on the reverse of this consignor's copy &amp; declare that contents on this way bill are true and correct. The To-pay Freight has may/our consent and will be paid by the Consignee along with Service Charges as applicable at the time of Delivery.</div>
        <div class="sg">Name : <span class="ln w1"></span></div>
        <div class="sg">Signature : <span class="ln w2"></span></div>
        <div class="sg">Received By ${esc(d.companyName)}.</div>
        <div class="sg">Name: <span class="ln w3"></span></div>
        <div class="sg sp"><span>Date</span><span>Time</span><span>Booking Incharge${d.createdByName ? ` : ${esc(d.createdByName)}` : ''}</span></div>
      </div>
      <div class="g-mid">
        <div class="h two">VOLUMETRIC CHARGE WEIGHT</div>
        <div class="lbh"><span>Length</span><span>Breadth</span><span>Height</span></div>
        <div class="lbh v"><span>${dims?.lengthCm ?? ''}</span><span>${dims?.widthCm ?? ''}</span><span>${dims?.heightCm ?? ''}</span></div>
        <div class="av"><div class="h">ACTUAL WEIGHT</div><div class="h">VOLUMETRIC</div></div>
        <div class="av v"><div>${num(actual)}</div><div>${num(volumetric)}</div></div>
        <div class="av"><div class="h">D.O.D</div><div class="h">C.O.D</div></div>
        <div class="av e"><div></div><div></div></div>
        <div class="mop"><b>MODE OF PAYMENT</b></div>
        <div class="chk">PAID ${tick(isPaid)} &nbsp; CREDIT ${tick(isCredit)} &nbsp; TO PAY ${tick(isToPay)}</div>
        <div class="tp"><b>TO PAY</b><br>Amount : ${isToPay ? total.toFixed(2) : '<span class="ln w4"></span>'}</div>
      </div>
      <div class="g-chg">
        <div class="h one">CHARGES</div>
        <div class="cr h2"><span class="hh">Freight</span></div>
        ${charges.slice(1).map(([n]) => `<div class="cr"><span>${n}</span></div>`).join('')}
      </div>
      <div class="g-amt">
        <div class="h one">FREIGHT Rs.</div>
        ${charges.map(([, v]) => `<div class="ar">${v}</div>`).join('')}
      </div>
      <div class="g-si">
        <div class="h one">SPECIAL INSTRUCTIONS</div>
        <div class="si">${esc(d.remarks)}</div>
        <div class="rcv">Received above shipment in order and good condition.<br><br>Date :<br>Time :<br>Receiver's Name, Sign &amp; Stamp</div>
        <div class="off">FOR OFFICE USE ONLY<br><br>Received By : <span class="ln w5"></span><br>Date : <span class="ln w5"></span><span class="tag">${label}</span></div>
      </div>
    </div>
  </div></div>`;
}

function renderHtml(list: ConsignmentPrintData[]): string {
  const body = list.flatMap((d) => COPIES.map((l) => copy(d, l)));
  // 3 slots per A4 page; the dashed line is each slot's bottom border.
  return `<!doctype html><html><head><meta charset="utf-8"><title>Shipment Copy</title>
<style>
  *{box-sizing:border-box}
  html,body{margin:0}
  body{font-family:Arial,Helvetica,sans-serif;color:#000;font-size:7.4px;-webkit-print-color-adjust:exact;print-color-adjust:exact}
  @page{size:A4 portrait;margin:6mm}
  .slot{height:94mm;padding:1.5mm 0;border-bottom:1px dashed #000;page-break-inside:avoid;overflow:hidden}
  .slot:nth-child(3n){page-break-after:always;border-bottom:none}
  .slot:last-child{page-break-after:auto}
  .lr{width:198mm;margin:0 auto;border:1px solid #000;height:100%;display:flex;flex-direction:column}
  .top{display:flex;height:17mm;border-bottom:2px solid #000}
  .logo{width:24%;border-right:1px solid #000;display:flex;align-items:center;justify-content:center;padding:1mm}
  .logo img{max-width:100%;max-height:100%}
  .addr{width:30%;padding:1mm 1.5mm;font-size:7.6px;line-height:1.3;border-right:2px solid #000}
  .meta{flex:1;display:flex;flex-direction:column}
  .mr{display:flex;flex:1;border-bottom:1px solid #000;align-items:stretch}
  .mr:last-child{border-bottom:none}
  .mk{width:21%;font-weight:700;font-size:7.6px;padding:1mm;border-right:1px solid #000;display:flex;align-items:center}
  .mv{flex:1;padding:0 1.5mm;display:flex;align-items:center;border-right:1px solid #000;font-weight:700}
  .mv:last-child{border-right:none}
  .big{font-size:11px}
  .h{background:#e6e6e6;font-weight:700;text-align:center;padding:.8mm;border-bottom:1px solid #000;font-size:7.6px}
  .h.one{height:6mm;display:flex;align-items:center;justify-content:center}
  .h.two{height:6mm;display:flex;align-items:center;justify-content:center}
  .party{display:flex;height:15mm;border-bottom:2px solid #000}
  .party>div{border-right:1px solid #000;display:flex;flex-direction:column}
  .party>div:last-child{border-right:none}
  .p1{width:40%}.p2{width:49%}.p3{flex:1}
  .party .h{text-align:left;padding-left:2mm}
  .party .b{flex:1;font-weight:700;font-size:12px;padding:1mm 2mm}
  .bc{flex:1;display:flex;align-items:center;justify-content:center;overflow:hidden}
  .bc{padding:.5mm}.bc svg{height:9.5mm;width:9.5mm}
  .grid{display:flex;flex:1;min-height:0}
  .g-left{width:36.4%;border-right:1px solid #000;display:flex;flex-direction:column}
  .wt,.wt2{display:flex}
  .wt>div,.wt2>div{flex:1;border-right:1px solid #000}
  .wt>div:last-child,.wt2>div:last-child{border-right:none}
  .c{text-align:center;font-weight:700;font-size:12px;padding:1mm;border-bottom:1px solid #000}
  .c.s{font-weight:400;font-size:8px;padding:.6mm}
  .risk{padding:.8mm 1mm;line-height:1.35;border-bottom:1px solid #000;font-size:7.4px}
  .terms{padding:1mm;line-height:1.25;font-size:7.4px}
  .sg{padding:.4mm 1mm;font-size:7.4px}
  .sg.sp{display:flex;justify-content:space-between;padding-right:4mm}
  .ln{display:inline-block;border-bottom:1px solid #000;height:7px;vertical-align:bottom}
  .w1{width:62%}.w2{width:24%}.w3{width:52%}.w4{width:20mm}.w5{width:22mm}
  .g-mid{width:15.5%;border-right:1px solid #000;display:flex;flex-direction:column}
  .lbh{display:flex;border-bottom:1px solid #000}
  .lbh span{flex:1;text-align:center;border-right:1px solid #000;font-weight:700;font-size:7px}
  .lbh span:last-child{border-right:none}
  .lbh.v{height:5mm}.lbh.v span{font-weight:400}
  .av{display:flex;border-bottom:1px solid #000}
  .av>div{flex:1;text-align:center;border-right:1px solid #000;padding:.6mm}
  .av>div:last-child{border-right:none}
  .av .h{border-bottom:none;background:none;font-size:7px}
  .av.v>div{font-weight:700;font-size:12px;padding:1.5mm}
  .av.e>div{height:6mm}
  .mop{padding:1mm;border-bottom:1px solid #000;font-size:8px;margin-top:2mm}
  .chk{padding:1mm .5mm;font-size:7px;white-space:nowrap;border-bottom:1px solid #000}
  .tp{padding:1mm;font-size:8px}
  .g-chg{width:9.5%;border-right:1px solid #000;display:flex;flex-direction:column}
  .g-amt{width:7.7%;border-right:1px solid #000;display:flex;flex-direction:column}
  .cr{flex:1;border-bottom:1px solid #000;display:flex;align-items:center;justify-content:center;font-size:7.6px;text-align:center}
  .cr.h2{font-weight:700}
  .ar{flex:1;border-bottom:1px solid #000;text-align:right;padding:0 1mm;display:flex;align-items:center;justify-content:flex-end;font-size:7.6px}
  .g-chg .cr:last-child,.g-amt .ar:last-child{border-bottom:none}
  .g-si{flex:1;display:flex;flex-direction:column}
  .si{height:15mm;padding:1.5mm;font-size:8px}
  .rcv{padding:1mm 1.5mm;font-weight:700;font-size:7.6px;border-top:1px solid #fff}
  .off{margin-top:auto;border-top:1px solid #000;padding:1mm 1.5mm;font-weight:700;font-size:7.6px;position:relative}
  .tag{position:absolute;right:1.5mm;bottom:1mm;font-size:7px}
  @media screen{ body{background:#eee;padding:10px} .slot{background:#fff} }
</style></head><body>${body.join('')}
<script>window.onload = () => setTimeout(() => window.print(), 50);</script>
</body></html>`;
}

/** Prints one or many shipments, three copies each, via a hidden iframe (popup-blocker
 *  safe, same mechanism as `printAmboxCopies`). */
export function printShipmentCopies(list: ConsignmentPrintData[]): void {
  if (!list.length) return;
  const iframe = document.createElement('iframe');
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
  document.body.appendChild(iframe);
  const cleanup = () => { iframe.parentNode?.removeChild(iframe); };
  iframe.contentWindow?.addEventListener('afterprint', cleanup);
  setTimeout(cleanup, 30_000);
  const doc = iframe.contentWindow?.document;
  if (!doc) { cleanup(); return; }
  doc.open();
  doc.write(renderHtml(list));
  doc.close();
}
