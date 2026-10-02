import { describe, expect, it } from 'vitest';
import { matchesScan, packageId, parsePackageId, renderLabelsHtml, ShipmentLabelData } from './shipment-label-print.util';

const data: ShipmentLabelData = {
  companyName: 'Acme', companyLogo: null, trackingNumber: 'AWB10001', bookingDate: '2026-10-02',
  bookingBranchLabel: 'Pune', destinationLabel: 'Raipur', destinationPincode: '492001',
  senderName: 'S', receiverName: 'R', receiverContact: '9999999999', receiverAddress: 'Addr',
  weight: 12, paymentModeLabel: 'Prepaid (PAID)', serviceTypeLabel: 'Express', routing: null, numberOfPackages: 3
};

describe('shipment labels', () => {
  it('builds and parses package ids', () => {
    expect(packageId('AWB10001', 2)).toBe('AWB10001-002');
    expect(parsePackageId('AWB10001-002')).toEqual({ awb: 'AWB10001', packageNo: 2 });
    expect(parsePackageId('AWB10001')).toBeNull();
  });
  it('renders one label per package, or only the requested ones', () => {
    const count = (h: string) => (h.match(/<section class="label">/g) ?? []).length;
    expect(count(renderLabelsHtml(data))).toBe(3);
    const one = renderLabelsHtml(data, [2]);
    expect(count(one)).toBe(1);
    expect(one).toContain('AWB10001-002');
    expect(one).toContain('2/3');
  });
  it('matches a scanned package id, AWB or shipment no. to its shipment', () => {
    const ship = { trackingNumber: 'AWB10001', shipmentNumber: 'SHP-1' };
    expect(matchesScan(ship, 'awb10001-002')).toBe(true);
    expect(matchesScan(ship, 'AWB10001')).toBe(true);
    expect(matchesScan(ship, 'SHP-1')).toBe(true);
    expect(matchesScan(ship, 'AWB10002-001')).toBe(false);
  });
});
