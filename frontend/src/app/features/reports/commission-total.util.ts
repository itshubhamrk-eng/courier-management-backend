import { BranchCommissionSummary } from '@core/models/shipment.model';

/** Sums `totalCommission` across every branch row `ShipmentService.commissionSummary`
 *  returns — shared by Booking/Delivery Report's single stat tile and Commission
 *  Report's own per-field totals reduce. */
export function totalCommissionOf(rows: BranchCommissionSummary[]): number {
  return rows.reduce((sum, r) => sum + r.totalCommission, 0);
}
