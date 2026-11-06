import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { AuthService } from '@core/auth/auth.service';

export type PrintDocType = 'DRS' | 'LOADING_SHEET' | 'THC';

export interface PrintEntry { printedByName: string | null; printedBy: string; printedAt: string; }
export interface PrintHistory { docType: PrintDocType; docRef: string; count: number; history: PrintEntry[]; }

/** Print tracking for DRS / Loading Sheet / THC — every open of the print tab is one row
 *  (who + when); the count is the row count. Backs /api/v1/print-logs. */
@Injectable({ providedIn: 'root' })
export class PrintLogService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private static readonly PATH = '/print-logs';

  /** Fire-and-forget: a failed log must never block the print itself. */
  record(docType: PrintDocType, docRef: string, docNumber?: string | null, done?: (h: PrintHistory) => void): void {
    this.api.post<PrintHistory>(PrintLogService.PATH, { docType, docRef, docNumber: docNumber ?? null })
      .subscribe({ next: (h) => done?.(h), error: () => undefined });
  }
  history(docType: PrintDocType, docRef: string): Observable<PrintHistory> {
    return this.api.get<PrintHistory>(PrintLogService.PATH, { docType, docRef });
  }
  counts(docType: PrintDocType, docRefs: string[]): Observable<Record<string, number>> {
    return this.api.get<Record<string, number>>(`${PrintLogService.PATH}/counts`, { docType, docRefs: docRefs.join(',') });
  }

  /** Logs the print, then writes the document into the already-open print tab with a
   *  "Printed by NAME · date · Print #N" line added, so every copy carries who printed it and
   *  whether it is the original or a reprint. If the log call fails the document still prints
   *  (with the signed-in user's name and no count). */
  printWindow(win: Window, html: string, docType: PrintDocType, docRef: string, docNumber: string | null,
              onCount?: (count: number) => void): void {
    const write = (name: string, count: number | null) => {
      const when = new Date().toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true });
      const esc = (t: string) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const copy = count == null ? '' : count === 1 ? ' · Print #1 (Original)' : ` · Print #${count} (Reprint)`;
      const line = `<div style="width:900px;max-width:100%;margin:8px auto 0;font:600 12px Arial,sans-serif;color:#111;text-align:right">Printed by ${esc(name || '—')} · ${esc(when)}${copy}</div>`;
      win.document.write(html.includes('</body>') ? html.replace('</body>', line + '</body>') : html + line);
      win.document.close();
      win.focus();
    };
    this.api.post<PrintHistory>(PrintLogService.PATH, { docType, docRef, docNumber }).subscribe({
      next: (h) => { onCount?.(h.count); write(h.history[0]?.printedByName ?? this.auth.displayName(), h.count); },
      error: () => write(this.auth.displayName(), null)
    });
  }
}
