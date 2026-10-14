import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { Branch } from '@core/models/branch.model';
import { PdpRate } from '@core/models/pdp.model';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { PdpService } from './pdp.service';

interface PartnerRateRow { partner: Branch; pickup: number; delivery: number; }

/** One row per partner with how many active pickup / delivery rate cards it has. */
@Component({
  selector: 'app-partner-rate-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiButton, UiTable],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Partner Rates</h1>
          <p class="text-caption">What each partner is paid — separate pickup and delivery rate cards with weight slabs.</p></div>
      </header>
      <app-table [columns]="columns" [rows]="rows()" [loading]="loading()"
                 emptyTitle="No partners" emptyHint="Create a Pickup-Delivery Partner first, then set its rates here."
                 (rowClick)="open($event)">
        <ng-template #row let-r>
          <td><strong>{{ r.partner.branchName }}</strong><div class="text-caption">{{ r.partner.branchCode }}</div></td>
          <td>{{ r.pickup ? r.pickup + ' active' : 'Not set' }}</td>
          <td>{{ r.delivery ? r.delivery + ' active' : 'Not set' }}</td>
          <td class="col-actions" (click)="$event.stopPropagation()">
            <app-button variant="stroked" icon="edit" (pressed)="open(r)">Rates</app-button>
          </td>
        </ng-template>
      </app-table>
    </div>
  `,
  styles: [`.col-actions{text-align:right;width:130px}`]
})
export class PartnerRateList implements OnInit {
  private readonly service = inject(PdpService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);

  protected readonly rows = signal<PartnerRateRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly columns: TableColumn<PartnerRateRow>[] = [
    { key: 'partner', header: 'Partner' },
    { key: 'pickup', header: 'Pickup Rates' },
    { key: 'delivery', header: 'Delivery Rates' },
    { key: 'actions', header: '', width: '130px', align: 'right' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Pickup Delivery Partner Management' }, { label: 'Partner Rates' }]);
    forkJoin({ partners: this.service.partners({ page: 0, size: 200 }), rates: this.service.rates() }).subscribe({
      next: ({ partners, rates }) => {
        const count = (id: string, t: PdpRate['rateType']) => (rates ?? []).filter((r) => r.branchId === id && r.rateType === t && r.status === 'ACTIVE').length;
        this.rows.set(partners.content.map((partner) => ({ partner, pickup: count(partner.id, 'PICKUP'), delivery: count(partner.id, 'DELIVERY') })));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected open(r: PartnerRateRow): void { this.router.navigate(['/pdp/rates', r.partner.id]); }
}
