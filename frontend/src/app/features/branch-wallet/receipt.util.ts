import { WalletResponse, WalletTransaction, formatMoney, prettyToken, subTypeLabel } from '@core/models/wallet.model';

/**
 * Build and download a self-contained HTML receipt for a settled wallet transaction. Nothing
 * is invented — every value is read off the real transaction and wallet. A dedicated backend
 * PDF endpoint can replace this later; until then the client renders exactly what it holds.
 */
export function downloadReceipt(txn: WalletTransaction, wallet: WalletResponse, appName: string): void {
  const money = formatMoney(txn.amount, wallet.currency);
  const when = new Date(txn.createdAt).toLocaleString('en-IN');
  const rows: Array<[string, string]> = [
    ['Receipt / Transaction No', txn.transactionNo],
    ['Date & Time', when],
    ['Wallet', `${wallet.walletNumber}${wallet.branchName ? ' · ' + wallet.branchName : ''}`],
    ['Type', txn.transactionTypeLabel || prettyToken(txn.transactionType)],
    ['Reason', txn.subTransactionTypeLabel || subTypeLabel(txn.subTransactionType)],
    ['Payment Reference', txn.paymentReference || '—'],
    ['Reference', txn.referenceId || '—'],
    ['Status', txn.paymentStatus ? prettyToken(txn.paymentStatus) : '—'],
    ['Balance After', formatMoney(txn.balanceAfter, wallet.currency)]
  ];
  const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));
  const body = rows.map(([k, v]) => `<tr><td class="k">${esc(k)}</td><td class="v">${esc(v)}</td></tr>`).join('');
  const sign = txn.transactionType === 'CR' ? '+' : '−';

  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Receipt ${esc(txn.transactionNo)}</title>
<style>
  *{box-sizing:border-box} body{font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;color:#0f172a;margin:0;padding:40px;background:#f1f5f9}
  .r{max-width:560px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden}
  .h{padding:24px 28px;background:linear-gradient(135deg,#4f46e5,#4338ca);color:#fff}
  .h h1{margin:0;font-size:18px} .h p{margin:4px 0 0;opacity:.85;font-size:13px}
  .amt{padding:24px 28px;text-align:center;border-bottom:1px solid #e2e8f0}
  .amt .n{font-size:34px;font-weight:800;letter-spacing:-.02em;color:${txn.transactionType === 'CR' ? '#059669' : '#dc2626'}}
  .amt .l{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:#64748b;margin-top:4px}
  table{width:100%;border-collapse:collapse} td{padding:11px 28px;border-bottom:1px solid #f1f5f9;font-size:13px;vertical-align:top}
  td.k{color:#64748b;width:45%} td.v{font-weight:600;text-align:right}
  .f{padding:18px 28px;font-size:11px;color:#94a3b8;text-align:center}
  @media print{body{background:#fff;padding:0}.r{border:0}}
</style></head><body>
  <div class="r">
    <div class="h"><h1>${esc(appName)}</h1><p>Wallet Transaction Receipt</p></div>
    <div class="amt"><div class="n">${sign}${esc(money)}</div><div class="l">${esc(txn.subTransactionTypeLabel || subTypeLabel(txn.subTransactionType))}</div></div>
    <table>${body}</table>
    <div class="f">This is a system-generated receipt. Generated ${new Date().toLocaleString('en-IN')}.</div>
  </div>
</body></html>`;

  const url = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `receipt-${txn.transactionNo}.html`;
  a.click();
  URL.revokeObjectURL(url);
}
