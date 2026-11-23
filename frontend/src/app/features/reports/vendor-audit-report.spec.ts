import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NotificationService } from '@core/services/notification.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { VendorAuditRow } from '@core/models/shipment.model';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentService } from '../shipment/shipment.service';
import { VendorAuditReport } from './vendor-audit-report';

const row = (over: Partial<VendorAuditRow> = {}): VendorAuditRow => ({
  branchId: 'b-1', paidOrderCount: 1, paidOrderQuantity: 1, paidOrderAmount: 10, topayOrderCount: 0,
  topayOrderQuantity: 0, topayOrderAmount: 0, paidCommission: 1, deliveryCommission: 1,
  totalBookedOrderCount: 1, totalDeliveredOrderCount: 1, bookingTotalCommission: 1, deliveryTotalCommission: 1,
  odaCharges: 0, otherCharges: 0, cancelledOrderCount: 0,
  openingBalance: 100, closingBalance: 250, rechargeCount: 2, rechargeAmount: 400, drsChargeCredited: 50,
  ...over
});

describe('VendorAuditReport wallet columns', () => {
  let fixture: ComponentFixture<VendorAuditReport>;
  const service = { vendorAudit: vi.fn() };

  const open = (rows: VendorAuditRow[]) => {
    service.vendorAudit.mockReturnValue(of(rows));
    TestBed.configureTestingModule({
      imports: [VendorAuditReport],
      providers: [
        provideNoopAnimations(),
        { provide: ShipmentService, useValue: service },
        { provide: NotificationService, useValue: { info: vi.fn() } },
        { provide: BreadcrumbService, useValue: { set: vi.fn() } },
        { provide: MasterDataService, useValue: { options: () => of([{ value: 'b-1', label: 'Pune' }]) } }
      ]
    });
    fixture = TestBed.createComponent(VendorAuditReport);
    fixture.detectChanges();
  };

  beforeEach(() => { vi.resetAllMocks(); TestBed.resetTestingModule(); });

  const tile = (label: string): string => {
    const stats = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.stat'));
    const el = stats.find((s) => s.querySelector('.stat__l')?.textContent?.trim() === label);
    return el?.querySelector('.stat__v')?.textContent?.trim() ?? '';
  };

  it('sums recharged and DRS credited into tiles, treating null as 0', () => {
    open([row(), row({ branchId: 'b-2', rechargeAmount: 100, drsChargeCredited: null }),
      row({ branchId: 'b-3', rechargeAmount: null, drsChargeCredited: 25 })]);
    expect(tile('Total Recharged')).toBe('₹500.00');
    expect(tile('Total DRS Credited')).toBe('₹75.00');
  });

  it('shows a dash for null wallet cells', () => {
    open([row({ openingBalance: null, closingBalance: null, rechargeCount: null, rechargeAmount: null, drsChargeCredited: null })]);
    const cells = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody td')).map((c) => c.textContent?.trim());
    expect(cells.slice(-4)).toEqual(['—', '—', '—', '—']);
  });

  it('adds the four wallet columns to the CSV export', async () => {
    let blob: Blob | undefined;
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:x'; });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    open([row()]);
    fixture.componentInstance.exportCsv();
    const lines = (await blob!.text()).replace('\uFEFF', '').split('\n');
    const header = lines[0].split(',').map((h) => h.replace(/"/g, ''));
    expect(header.slice(-4)).toEqual(['openingBalance', 'rechargeCount', 'drsChargeCredited', 'closingBalance']);
    expect(lines[1].split(',').slice(-4).map((c) => c.replace(/"/g, ''))).toEqual(['100', '2', '50', '250']);
  });
});
