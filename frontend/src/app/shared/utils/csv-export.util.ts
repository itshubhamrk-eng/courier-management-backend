/**
 * Builds a CSV file and triggers a browser download. When `numericCols` is
 * given (0-based header indices), appends a final "Total" row summing those
 * columns across all data rows — skipped entirely when there are no rows.
 */
export function downloadCsv(
  filename: string,
  header: string[],
  rows: (string | number | boolean | null | undefined)[][],
  numericCols: number[] = []
): void {
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const lines = [header.map(esc).join(','), ...rows.map((r) => r.map(esc).join(','))];
  if (rows.length) {
    const total = header.map((_, i) => {
      if (i === 0) return 'Total';
      if (!numericCols.includes(i)) return '';
      const sum = rows.reduce((s, r) => s + (Number(r[i]) || 0), 0);
      return Math.round(sum * 100) / 100;
    });
    lines.push(total.map(esc).join(','));
  }
  const csv = lines.join('\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
