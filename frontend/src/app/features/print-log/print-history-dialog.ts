import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { PrintDocType, PrintHistory, PrintLogService } from './print-log.service';

export interface PrintHistoryData { docType: PrintDocType; docRef: string; title: string; }

/** Who printed a DRS / Loading Sheet / THC, when, and how many times in total. */
@Component({
  selector: 'app-print-history-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatDialogModule, UiButton],
  template: `
    <div class="ph">
      <h2 class="text-h2">Print history — {{ data.title }}</h2>
      @if (loading()) {
        <p class="text-caption">Loading…</p>
      } @else if (!h() || !h()!.count) {
        <p class="text-caption">Not printed yet.</p>
      } @else {
        <p class="ph__count">Printed <strong>{{ h()!.count }}</strong> time(s)</p>
        <div class="ph__scroll">
          <table class="ph__tbl">
            <thead><tr><th>#</th><th>Printed By</th><th>Date &amp; Time</th></tr></thead>
            <tbody>
              @for (e of h()!.history; track $index; let i = $index) {
                <tr><td>{{ h()!.count - i }}{{ i === h()!.count - 1 ? ' (original)' : '' }}</td>
                  <td>{{ e.printedByName || '—' }}</td><td>{{ e.printedAt | date:'dd MMM yyyy, hh:mm a' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
      <div class="ph__actions"><app-button (pressed)="ref.close()">Close</app-button></div>
    </div>
  `,
  styles: [`
    .ph { padding:24px; width:520px; max-width:92vw; display:flex; flex-direction:column; gap:14px; }
    .ph__count { margin:0; font:500 17px var(--font-sans); }
    .ph__scroll { max-height:50vh; overflow:auto; }
    .ph__tbl { width:100%; border-collapse:collapse; font:400 16px var(--font-sans); }
    .ph__tbl th, .ph__tbl td { text-align:left; padding:6px 8px; border-bottom:1px solid var(--surface-border); }
    .ph__actions { display:flex; justify-content:flex-end; }
  `]
})
export class PrintHistoryDialog implements OnInit {
  protected readonly data = inject<PrintHistoryData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<PrintHistoryDialog>);
  private readonly svc = inject(PrintLogService);
  protected readonly loading = signal(true);
  protected readonly h = signal<PrintHistory | null>(null);

  ngOnInit(): void {
    this.svc.history(this.data.docType, this.data.docRef).subscribe({
      next: (r) => { this.h.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
