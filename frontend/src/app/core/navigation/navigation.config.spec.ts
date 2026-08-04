import { describe, expect, it } from 'vitest';
import { AppRole } from '../models/role.model';
import { NAVIGATION } from './navigation.config';
import { NavNode } from './navigation.model';

function leaf(id: string): NavNode {
  const stack = [...NAVIGATION];
  while (stack.length) {
    const node = stack.shift()!;
    if (node.id === id) {
      return node;
    }
    if (node.children) {
      stack.push(...node.children);
    }
  }
  throw new Error(`No nav node with id "${id}"`);
}

/**
 * The branch's four staff roles (Branch Manager, Booking Operator, Delivery Operator,
 * Accounts) each hold a different slice of DefaultRoleCatalog's permissions, so each
 * should see a different slice of the nav. These pin that mapping down.
 */
describe('NAVIGATION — branch staff visibility', () => {
  // Shipment Movement (V19) split "the road" in two: Out Scan/Dispatch happen at the
  // booking branch (before the run leaves), In Scan/Out For Delivery/Deliver at the
  // delivery branch (after it arrives) — see MEMORY/modules/shipment-movement.md. The
  // aspirational placeholder this replaced had guessed the whole block was delivery-desk
  // work; the real module's own business flow says otherwise for the first two steps.
  it('the booking desk sees booking, out scan and dispatch, not masters or the delivery-branch steps', () => {
    expect(leaf('booking').roles).toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('manifest').roles).toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('dispatch').roles).toContain(AppRole.BOOKING_OPERATOR);
    // Masters is company-admin-only now — not even the booking desk's own lists.
    expect(leaf('vehicle-types').roles).not.toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('routes').roles).not.toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('receive').roles).not.toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('out-for-delivery').roles).not.toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('delivery').roles).not.toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('branch-wallet').roles).not.toContain(AppRole.BOOKING_OPERATOR);
  });

  it('the road sees in scan through delivery, not booking, out scan/dispatch, or masters', () => {
    expect(leaf('receive').roles).toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('out-for-delivery').roles).toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('delivery').roles).toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('manifest').roles).not.toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('dispatch').roles).not.toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('booking').roles).not.toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('vehicle-types').roles).not.toContain(AppRole.DELIVERY_OPERATOR);
    expect(leaf('branch-wallet').roles).not.toContain(AppRole.DELIVERY_OPERATOR);
  });

  it('the money desk sees the wallet, payment and invoice, not the road', () => {
    expect(leaf('branch-wallet').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('wallet-transactions').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('payment').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('invoice').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('finance-reports').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('dispatch').roles).not.toContain(AppRole.ACCOUNTS);
    expect(leaf('booking').roles).not.toContain(AppRole.ACCOUNTS);
  });

  it('every branch staff role reads the branch and shipment reports', () => {
    for (const role of [AppRole.BRANCH_MANAGER, AppRole.BOOKING_OPERATOR,
      AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS]) {
      expect(leaf('reports-dashboard').roles).toContain(role);
      expect(leaf('branch-reports').roles).toContain(role);
    }
  });

  it('none of the four branch-staff roles reach Administration', () => {
    for (const role of [AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS]) {
      expect(leaf('users').roles).not.toContain(role);
      expect(leaf('roles').roles).not.toContain(role);
      expect(leaf('permissions').roles).not.toContain(role);
    }
  });

  it('customers is every company role, SUPER_ADMIN excluded', () => {
    expect(leaf('customers').roles).not.toContain(AppRole.SUPER_ADMIN);
    expect(leaf('customers').roles).toContain(AppRole.BOOKING_OPERATOR);
    expect(leaf('customers').roles).toContain(AppRole.DELIVERY_OPERATOR);
  });
});

/**
 * SUPER_ADMIN is the platform tier — a company's own setup (Rate Master, Company
 * Settings, Branches, Masters) and day-to-day operations are not theirs to see.
 */
describe('NAVIGATION — SUPER_ADMIN excluded from company-side sections', () => {
  it('Rate Master is company and branch only', () => {
    for (const id of ['rates', 'rate-calculator']) {
      expect(leaf(id).roles).not.toContain(AppRole.SUPER_ADMIN);
      expect(leaf(id).roles).toContain(AppRole.COMPANY_ADMIN);
      expect(leaf(id).roles).toContain(AppRole.BRANCH_MANAGER);
    }
  });

  it('Company Settings, Branches and Masters are COMPANY_ADMIN only', () => {
    for (const id of ['company-settings', 'settings', 'branches',
      'vehicle-types', 'package-types', 'service-types', 'payment-modes', 'weight-slabs', 'routes']) {
      expect(leaf(id).roles).toEqual([AppRole.COMPANY_ADMIN]);
    }
  });

  it('Operations excludes SUPER_ADMIN but keeps COMPANY_ADMIN', () => {
    for (const id of ['booking', 'shipment-search', 'manifest', 'receive', 'dispatch',
      'out-for-delivery', 'delivery']) {
      expect(leaf(id).roles).not.toContain(AppRole.SUPER_ADMIN);
      expect(leaf(id).roles).toContain(AppRole.COMPANY_ADMIN);
    }
  });

  it('Finance excludes SUPER_ADMIN but keeps branch/finance roles', () => {
    expect(leaf('branch-wallet').roles).not.toContain(AppRole.SUPER_ADMIN);
    expect(leaf('branch-wallet').roles).toContain(AppRole.BRANCH_MANAGER);
    expect(leaf('branch-wallet').roles).toContain(AppRole.ACCOUNTS);
    expect(leaf('hub-wallet').roles).not.toContain(AppRole.SUPER_ADMIN);
    expect(leaf('hub-wallet').roles).toContain(AppRole.FINANCE_USER);
  });

  it('Pricing (aspirational) is COMPANY_ADMIN only', () => {
    for (const id of ['rate-cards', 'zone-pricing', 'surcharges']) {
      expect(leaf(id).roles).toEqual([AppRole.COMPANY_ADMIN]);
    }
  });

  it('the shared geography (under Masters) reads for SUPER_ADMIN and COMPANY_ADMIN, unlike the company-only catalogues', () => {
    for (const id of ['global-countries', 'global-states', 'global-districts', 'global-cities', 'global-areas', 'global-pincodes']) {
      expect(leaf(id).roles).toEqual([AppRole.SUPER_ADMIN, AppRole.COMPANY_ADMIN]);
    }
    for (const id of ['vehicle-types', 'package-types', 'service-types', 'payment-modes', 'weight-slabs', 'routes']) {
      expect(leaf(id).roles).toEqual([AppRole.COMPANY_ADMIN]);
    }
  });
});
