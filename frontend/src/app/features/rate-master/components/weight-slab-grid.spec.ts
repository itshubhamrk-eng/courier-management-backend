import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { Rate } from '@core/models/rate.model';
import { WeightSlabGrid } from './weight-slab-grid';

function rate(id: string, min: number, max: number, status: 'ACTIVE' | 'INACTIVE' = 'ACTIVE'): Rate {
  return {
    id, rateCode: id, rateName: id, routeId: 'r-1', serviceTypeId: 's-1', packageTypeId: 'p-1',
    paymentModeId: 'pm-1', minimumWeight: min, maximumWeight: max, weightUnit: 'KG',
    baseRate: 100, status, effectiveFrom: '2026-01-01', version: 0
  };
}

/** The client-side mirror of RateServiceImpl.requireNoOverlap — flags an active slab that
 *  shares weight with another active slab, so an admin sees the conflict before saving. */
describe('WeightSlabGrid — overlap detection', () => {
  let fixture: ComponentFixture<WeightSlabGrid>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [WeightSlabGrid] });
    fixture = TestBed.createComponent(WeightSlabGrid);
  });

  const rows = () => (fixture.componentInstance as unknown as { slabRows: () => { rate: Rate; overlapsAnother: boolean }[] }).slabRows();

  it('adjacent active slabs do not conflict', () => {
    fixture.componentRef.setInput('rows', [rate('A', 0, 5), rate('B', 5, 10)]);
    fixture.detectChanges();

    expect(rows().every((r) => !r.overlapsAnother)).toBe(true);
  });

  it('overlapping active slabs are both flagged', () => {
    fixture.componentRef.setInput('rows', [rate('A', 0, 5), rate('B', 3, 8)]);
    fixture.detectChanges();

    const flagged = rows().filter((r) => r.overlapsAnother).map((r) => r.rate.id);
    expect(flagged.sort()).toEqual(['A', 'B']);
  });

  it('an inactive slab never conflicts, even if its range overlaps an active one', () => {
    fixture.componentRef.setInput('rows', [rate('A', 0, 5), rate('B', 3, 8, 'INACTIVE')]);
    fixture.detectChanges();

    expect(rows().every((r) => !r.overlapsAnother)).toBe(true);
  });

  it('rows are sorted by minimum weight regardless of input order', () => {
    fixture.componentRef.setInput('rows', [rate('HIGH', 5, 10), rate('LOW', 0, 5)]);
    fixture.detectChanges();

    expect(rows().map((r) => r.rate.id)).toEqual(['LOW', 'HIGH']);
  });
});
