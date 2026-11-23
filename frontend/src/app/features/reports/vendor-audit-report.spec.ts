import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NotificationService } from '@core/services/notification.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { VendorAuditRow, BranchSendStatus, SendOutcome } from '@core/models/shipment.model';
import { MasterDataService } from '@features/masters/master-data.service';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
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
  const service = { vendorAudit: vi.fn(), vendorAuditSendStatus: vi.fn() };

  const open = (rows: VendorAuditRow[]) => {
    service.vendorAudit.mockReturnValue(of(rows));
    service.vendorAuditSendStatus.mockReturnValue(of([]));
    TestBed.configureTestingModule({
      imports: [VendorAuditReport],
      providers: [
        provideNoopAnimations(),
        { provide: ShipmentService, useValue: service },
        { provide: NotificationService, useValue: { info: vi.fn() } },
        { provide: DialogService, useValue: { confirm: vi.fn() } },
        { provide: BreadcrumbService, useValue: { set: vi.fn() } },
        { provide: MasterDataService, useValue: { options: () => of([{ value: 'b-1', label: 'Pune' }]) } }
      ]
    });
    fixture = TestBed.createComponent(VendorAuditReport);
    fixture.detectChanges();
  };

  beforeEach(() => { vi.resetAllMocks(); TestBed.resetTestingModule(); });
  afterEach(() => { vi.restoreAllMocks(); });

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
    expect(cells.slice(-6, -2)).toEqual(['—', '—', '—', '—']);
  });

  it('adds the four wallet columns to the CSV export', async () => {
    let blob: Blob | undefined;
    vi.spyOn(URL, 'createObjectURL').mockImplementation((b: Blob | MediaSource) => { blob = b as Blob; return 'blob:x'; });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    open([row()]);
    fixture.componentInstance.exportCsv();
    const lines = (await blob!.text()).replace('\uFEFF', '').split('\n');
    const header = lines[0].split(',').map((h) => h.replace(/"/g, ''));
    expect(header.slice(-4)).toEqual(['openingBalance', 'rechargeCount', 'drsChargeCredited', 'closingBalance']);
    expect(lines[1].split(',').slice(-4).map((c) => c.replace(/"/g, ''))).toEqual(['100', '2', '50', '250']);
  });
});


describe('VendorAuditReport WhatsApp send', () => {
  let fixture: ComponentFixture<VendorAuditReport>;
  const service = {
    vendorAudit: vi.fn(), vendorAuditSendStatus: vi.fn(), vendorAuditPdf: vi.fn(),
    sendVendorAuditReport: vi.fn(), sendVendorAuditReportToAll: vi.fn()
  };
  const notify = { info: vi.fn(), success: vi.fn(), warn: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };

  const status = (over: Partial<BranchSendStatus> = {}): BranchSendStatus =>
    ({ branchId: 'b-1', branchName: 'Pune', managerPhoneCount: 1, lastStatus: null, lastSentAt: null, ...over });
  const outcome = (result: SendOutcome['result'], branchId = 'b-1'): SendOutcome =>
    ({ branchId, branchName: 'Pune', result });

  const el = () => fixture.nativeElement as HTMLElement;
  const btn = (label: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.includes(label))!;
  const setDates = () => {
    const [from, to] = Array.from(el().querySelectorAll<HTMLInputElement>('input[type=date]'));
    from.value = '2026-11-01'; from.dispatchEvent(new Event('change'));
    to.value = '2026-11-30'; to.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const open = (statuses: BranchSendStatus[] | 'fail', dates = true) => {
    service.vendorAudit.mockReturnValue(of([row()]));
    service.vendorAuditSendStatus.mockReturnValue(
      statuses === 'fail' ? throwError(() => ({ error: { message: 'Bad range' } })) : of(statuses));
    TestBed.configureTestingModule({
      imports: [VendorAuditReport],
      providers: [
        provideNoopAnimations(),
        { provide: ShipmentService, useValue: service },
        { provide: NotificationService, useValue: notify },
        { provide: DialogService, useValue: dialogs },
        { provide: BreadcrumbService, useValue: { set: vi.fn() } },
        { provide: MasterDataService, useValue: { options: () => of([{ value: 'b-1', label: 'Pune' }]) } }
      ]
    });
    fixture = TestBed.createComponent(VendorAuditReport);
    fixture.detectChanges();
    if (dates) setDates();
  };

  beforeEach(() => { vi.resetAllMocks(); TestBed.resetTestingModule(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('renders Download PDF and Send on WhatsApp per row', () => {
    open([status()]);
    expect(btn('Download PDF')).toBeTruthy();
    expect(btn('Send on WhatsApp')).toBeTruthy();
    expect(btn('Send on WhatsApp').disabled).toBe(false);
  });

  it('disables Send with a No phone caption when the branch has no manager phone', () => {
    open([status({ managerPhoneCount: 0 })]);
    expect(btn('Send on WhatsApp').disabled).toBe(true);
    expect(el().textContent).toContain('No phone');
  });

  it('disables all send buttons while a date is empty', () => {
    open([status()], false);
    expect(btn('Download PDF').disabled).toBe(true);
    expect(btn('Send on WhatsApp').disabled).toBe(true);
    expect(btn('Send to all branches').disabled).toBe(true);
    expect(el().innerHTML).toContain('Pick a From and To date to send reports.');
  });

  it('shows the last status in plain words', () => {
    open([status({ lastStatus: 'DELIVERED', lastSentAt: '2026-11-02T10:00:00Z' })]);
    expect(el().textContent).toContain('Delivered');
  });

  it('shows Not sent when never attempted', () => {
    open([status()]);
    expect(el().textContent).toContain('Not sent');
  });

  it('asks for confirmation on a SENT row, then sends with force=true', () => {
    dialogs.confirm.mockReturnValue(of(true));
    service.sendVendorAuditReport.mockReturnValue(of(outcome('SENT')));
    open([status({ lastStatus: 'SENT' })]);
    btn('Send on WhatsApp').click();
    expect(dialogs.confirm).toHaveBeenCalledTimes(1);
    expect(service.sendVendorAuditReport).toHaveBeenCalledWith('b-1', '2026-11-01', '2026-11-30', true);
  });

  it('sends nothing when the confirmation is declined', () => {
    dialogs.confirm.mockReturnValue(of(false));
    open([status({ lastStatus: 'DELIVERED' })]);
    btn('Send on WhatsApp').click();
    expect(service.sendVendorAuditReport).not.toHaveBeenCalled();
  });

  it('sends a not-sent row with force=false and no confirmation', () => {
    service.sendVendorAuditReport.mockReturnValue(of(outcome('SENT')));
    open([status()]);
    btn('Send on WhatsApp').click();
    expect(dialogs.confirm).not.toHaveBeenCalled();
    expect(service.sendVendorAuditReport).toHaveBeenCalledWith('b-1', '2026-11-01', '2026-11-30', false);
    expect(notify.success).toHaveBeenCalledWith('Report sent to Pune on WhatsApp');
  });

  it('on ALREADY_SENT confirms and resends with force=true', () => {
    dialogs.confirm.mockReturnValue(of(true));
    service.sendVendorAuditReport
      .mockReturnValueOnce(of(outcome('ALREADY_SENT'))).mockReturnValueOnce(of(outcome('SENT')));
    open([status()]);
    btn('Send on WhatsApp').click();
    expect(dialogs.confirm).toHaveBeenCalledTimes(1);
    expect(service.sendVendorAuditReport).toHaveBeenNthCalledWith(2, 'b-1', '2026-11-01', '2026-11-30', true);
  });

  it('on ALREADY_SENT declined shows no message and calls once', () => {
    dialogs.confirm.mockReturnValue(of(false));
    service.sendVendorAuditReport.mockReturnValue(of(outcome('ALREADY_SENT')));
    open([status()]);
    btn('Send on WhatsApp').click();
    expect(service.sendVendorAuditReport).toHaveBeenCalledTimes(1);
    expect(notify.success).not.toHaveBeenCalled();
    expect(notify.warn).not.toHaveBeenCalled();
    expect(notify.error).not.toHaveBeenCalled();
  });

  it.each([
    ['NO_PHONE', 'warn', 'No manager phone number for Pune'],
    ['TEMPLATE_NOT_APPROVED', 'warn',
      'Template not approved by Meta yet. Create branch_monthly_report in WhatsApp Manager and check its status in Communication > Templates.'],
    ['FAILED', 'error', 'Could not send the report to Pune. Check Communication > Dashboard for the reason.']
  ] as const)('shows the %s outcome message', (result, kind, text) => {
    service.sendVendorAuditReport.mockReturnValue(of(outcome(result)));
    open([status()]);
    btn('Send on WhatsApp').click();
    expect(notify[kind]).toHaveBeenCalledWith(text);
  });

  it('reloads the status after a send', () => {
    service.sendVendorAuditReport.mockReturnValue(of(outcome('SENT')));
    open([status()]);
    const before = service.vendorAuditSendStatus.mock.calls.length;
    btn('Send on WhatsApp').click();
    expect(service.vendorAuditSendStatus.mock.calls.length).toBe(before + 1);
  });

  it('shows the error message when a send call fails', () => {
    service.sendVendorAuditReport.mockReturnValue(throwError(() => ({ error: { message: 'Nope' } })));
    open([status()]);
    btn('Send on WhatsApp').click();
    expect(notify.error).toHaveBeenCalledWith('Nope');
  });

  it('send to all shows a count summary', () => {
    service.sendVendorAuditReportToAll.mockReturnValue(of([
      outcome('SENT', 'a'), outcome('SENT', 'b'), outcome('SENT', 'c'), outcome('NO_PHONE', 'd')]));
    open([status()]);
    btn('Send to all branches').click();
    expect(service.sendVendorAuditReportToAll).toHaveBeenCalledWith('2026-11-01', '2026-11-30', false);
    expect(notify.success).toHaveBeenCalledWith('Sent 3, no phone 1, failed 0');
  });

  it('keeps the table and shows the error when the status call fails', () => {
    open('fail');
    expect(notify.error).toHaveBeenCalledWith('Bad range');
    expect(el().querySelectorAll('tbody tr').length).toBeGreaterThan(0);
    expect(el().textContent).toContain('Pune');
  });

  it('downloads the PDF through an anchor with the filename', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pdf');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let name = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { name = this.download; });
    service.vendorAuditPdf.mockReturnValue(of({ blob: new Blob(['x']), filename: 'pune.pdf' }));
    open([status()]);
    btn('Download PDF').click();
    expect(service.vendorAuditPdf).toHaveBeenCalledWith('b-1', '2026-11-01', '2026-11-30');
    expect(name).toBe('pune.pdf');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:pdf');
  });
});
