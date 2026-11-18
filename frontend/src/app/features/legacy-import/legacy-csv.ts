import { LegacyPreviewRow } from '@core/models/legacy-import.model';

/** A cell Excel would run as a formula ("=", "+", "-", "@", tab, CR) is neutralised with a leading apostrophe. */
export function csvCell(value: unknown): string {
  let text = String(value ?? '');
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

/** The per-row import report: one line per file row, with its outcome, reason and differences. */
export function legacyReportCsv(rows: LegacyPreviewRow[]): string {
  const header = ['Row', 'Consignment No', 'Origin', 'Outcome', 'Reason', 'Differences'];
  const lines = rows.map((r) =>
    [r.rowNo, r.consignmentNo, r.origin, r.outcome, r.reason ?? '', r.differences.join(' | ')].map(csvCell).join(','));
  return [header.map(csvCell).join(','), ...lines].join('\n');
}

export function downloadLegacyReport(filename: string, rows: LegacyPreviewRow[]): void {
  // UTF-8 BOM so Excel shows non-ASCII names correctly (same reason as csv-export.util).
  const url = URL.createObjectURL(new Blob(['﻿', legacyReportCsv(rows)], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
