import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ItemEntryGrid, ItemRow } from './item-entry-grid';

interface Internals {
  patch(index: number, changes: Partial<ItemRow>): void;
  add(): void;
}

/**
 * The client-side preview mirror of the Pricing Engine's own
 * VolumetricCalculator/ChargeableWeightCalculator: volumetric = l*w*h/5000 per item
 * (multiplied by quantity), chargeable = max(actual, volumetric). The figures that
 * actually book still come from the server — this only checks the wizard shows the
 * right preview before that round-trip.
 */
describe('ItemEntryGrid — weight preview', () => {
  let fixture: ComponentFixture<ItemEntryGrid>;
  let internals: Internals;
  let weight: { actual: number; volumetric: number; chargeable: number };
  let items: unknown[];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ItemEntryGrid] });
    fixture = TestBed.createComponent(ItemEntryGrid);
    internals = fixture.componentInstance as unknown as Internals;
    // Subscribed before the first change detection so the initial emission (a blank
    // row) is captured too — `output()` does not replay past values to late subscribers.
    fixture.componentInstance.weightChange.subscribe((w) => (weight = w));
    fixture.componentInstance.itemsChange.subscribe((i) => (items = i));
    fixture.detectChanges();
  });

  it('a blank starting row previews the default weight but emits no items', () => {
    // DEFAULT_WEIGHT_KG (5) prefills every new row so the preview isn't a misleading
    // zero; it only becomes a real item once itemName is filled in.
    expect(weight).toEqual({ actual: 5, volumetric: 0, chargeable: 5 });
    expect(items).toEqual([]);
  });

  it('actual weight sums weight × quantity across every row', () => {
    internals.patch(0, { itemName: 'Box', quantity: 2, weight: 3 });
    fixture.detectChanges();

    expect(weight.actual).toBeCloseTo(6, 3);
  });

  it('volumetric weight is l×w×h/5000, and chargeable picks whichever is larger', () => {
    // 40×30×20 / 5000 = 4.8kg volumetric, above a 2kg actual weight.
    internals.patch(0, { itemName: 'Box', quantity: 1, weight: 2, lengthCm: 40, widthCm: 30, heightCm: 20 });
    fixture.detectChanges();

    expect(weight.actual).toBeCloseTo(2, 3);
    expect(weight.volumetric).toBeCloseTo(4.8, 3);
    expect(weight.chargeable).toBeCloseTo(4.8, 3);
  });

  it('a row missing any one dimension contributes no volumetric weight, same as a single parcel', () => {
    internals.patch(0, { itemName: 'Box', quantity: 1, weight: 5, lengthCm: 40, widthCm: 30, heightCm: null });
    fixture.detectChanges();

    expect(weight.volumetric).toBe(0);
    expect(weight.chargeable).toBeCloseTo(5, 3);
  });

  it('a second added row without a name/weight is dropped from the emitted item list', () => {
    internals.patch(0, { itemName: 'Box', quantity: 1, weight: 2 });
    internals.add();
    fixture.detectChanges();

    expect(items.length).toBe(1);
  });
});
