import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { CompanyOverview } from './company-overview';
import { CompanyOverview as CompanyOverviewData } from '../models/dashboard.model';

const overview = (over: Partial<CompanyOverviewData> = {}): CompanyOverviewData => ({
  pipeline: [
    { stage: 'BOOKED', count: 10 }, { stage: 'READY_FOR_MANIFEST', count: 2 },
    { stage: 'MANIFEST_CREATED', count: 3 }, { stage: 'DISPATCHED', count: 4 },
    { stage: 'IN_SCAN', count: 1 }, { stage: 'OUT_FOR_DELIVERY', count: 2 },
    { stage: 'DELIVERED', count: 20 }
  ],
  readyForManifest: 0, manifestsAwaitingDispatch: 0, pendingDelivery: 0, delayedShipments: 0,
  totalWalletBalance: 12500, lowBalanceBranches: 0, topRoutes: [], topCustomers: [],
  ...over
});

describe('CompanyOverview', () => {
  let fixture: ComponentFixture<CompanyOverview>;
  let component: CompanyOverview;

  const build = (data: CompanyOverviewData | null, loading = false) => {
    fixture = TestBed.createComponent(CompanyOverview);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('loading', loading);
    fixture.componentRef.setInput('data', data);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CompanyOverview],
      providers: [provideRouter([])]
    });
  });

  it('shows a loader while loading', () => {
    build(null, true);
    expect(fixture.nativeElement.querySelector('app-loader')).toBeTruthy();
  });

  it('shows an empty state when there is no company overview yet', () => {
    build(null, false);
    expect(fixture.nativeElement.textContent).toContain('No company-wide data yet');
  });

  it('derives no action items when every backlog figure is zero', () => {
    build(overview());
    expect(component.actionItems()).toEqual([]);
  });

  it('surfaces only the non-zero backlog figures as action items, each with a real route', () => {
    build(overview({ readyForManifest: 5, delayedShipments: 3, lowBalanceBranches: 1 }));

    const items = component.actionItems();
    expect(items.map((i) => i.key)).toEqual(['manifest', 'delayed', 'lowBalance']);
    expect(items.find((i) => i.key === 'manifest')?.route).toBe('/movement/loading-sheet');
    expect(items.find((i) => i.key === 'lowBalance')?.route).toBe('/finance/branch-wallet');
  });

  it('formats a pipeline stage code into a human label', () => {
    expect(component.stageLabel('READY_FOR_MANIFEST')).toBe('Ready For Manifest');
    expect(component.stageLabel('DELIVERED')).toBe('Delivered');
  });

  it('renders top routes and top customers when present', () => {
    build(overview({
      topRoutes: [{ branchId: 'b-1', branchCode: 'BOM', branchName: 'Mumbai', shipmentCount: 9, revenue: 4500 }],
      topCustomers: [{ customerName: 'Acme Traders', customerContact: '9999999999', shipmentCount: 6, revenue: 3000 }]
    }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Mumbai');
    expect(text).toContain('Acme Traders');
  });
});
