import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { driver, type DriveStep } from 'driver.js';
import { AuthService } from '@core/auth/auth.service';
import { AppRole } from '@core/models/role.model';
import { storage } from '@core/utils/storage.util';

const SEEN_KEY = 'cs.tour.branchManager.seen';

/** `data.route`, when set, is navigated to before the step is highlighted — see `start()`. */
type TourStep = DriveStep & { data?: { route?: string } };

const BRANCH_MANAGER_STEPS: TourStep[] = [
  {
    popover: {
      title: 'Welcome to Courier SaaS',
      description: "A full walkthrough of everything your branch runs day to day — booking through delivery. Esc anytime to skip."
    }
  },
  {
    data: { route: '/dashboard' },
    element: '[data-tour="dash-stats"]',
    popover: { title: "Today's numbers", description: "Wallet balance, bookings, pending deliveries and today's collection — check these first every morning.", side: 'bottom' }
  },
  {
    data: { route: '/dashboard' },
    element: '[data-tour="dash-quick-actions"]',
    popover: { title: 'Quick Actions', description: 'The fastest way to book, search or track a shipment without digging through the menu.', side: 'top' }
  },
  {
    data: { route: '/users' },
    element: '[data-tour="users-head"]',
    popover: { title: 'Staff your branch', description: 'Add users and assign roles — the only place a branch manager manages people.', side: 'bottom' }
  },
  {
    data: { route: '/rates' },
    element: '[data-tour="rates-head"]',
    popover: { title: 'Rate Master', description: 'Look up a rate card, or use the calculator to price a shipment before you book it.', side: 'bottom' }
  },
  {
    data: { route: '/shipments/new' },
    element: '[data-tour="booking-head"]',
    popover: { title: 'Book a Shipment', description: 'Fill in the booking — the summary on the right prices it live as you type.', side: 'bottom' }
  },
  {
    data: { route: '/shipments' },
    element: '[data-tour="shipment-search-head"]',
    popover: { title: 'Shipment Search', description: 'Find any shipment your branch has booked — sort, filter, export to CSV.', side: 'bottom' }
  },
  {
    data: { route: '/track' },
    element: '[data-tour="track-head"]',
    popover: { title: 'Track Shipment', description: 'Look up a single AWB or shipment number and see its full status.', side: 'bottom' }
  },
  {
    data: { route: '/movement/out-scan' },
    element: '[data-tour="out-scan-head"]',
    popover: { title: 'Out Scan', description: 'Batch your booked shipments bound for one branch into a manifest — this is the out-scan.', side: 'bottom' }
  },
  {
    data: { route: '/movement/dispatch' },
    element: '[data-tour="dispatch-head"]',
    popover: { title: 'Dispatch', description: 'Assign a vehicle and driver to a manifest and send it out — its last stop at your branch.', side: 'bottom' }
  },
  {
    data: { route: '/movement/in-scan' },
    element: '[data-tour="in-scan-head"]',
    popover: { title: 'In Scan', description: 'Receive an inbound manifest arriving at your branch — scan it in to take custody.', side: 'bottom' }
  },
  {
    data: { route: '/movement/pending-delivery' },
    element: '[data-tour="pending-delivery-head"]',
    popover: { title: 'Pending Delivery', description: 'Everything received but not yet delivered — hand it to a delivery run, or close it out.', side: 'bottom' }
  },
  {
    data: { route: '/movement/out-for-delivery' },
    element: '[data-tour="out-for-delivery-head"]',
    popover: { title: 'Out For Delivery', description: 'Bulk-assign a batch of received shipments to a delivery run in one go.', side: 'bottom' }
  },
  {
    data: { route: '/movement/delivery' },
    element: '[data-tour="delivery-head"]',
    popover: { title: 'Delivery', description: "Close out a delivery against the consignee — receiver name, remarks, proof of delivery.", side: 'bottom' }
  },
  {
    data: { route: '/finance/branch-wallet' },
    element: '[data-tour="wallet-grid"]',
    popover: { title: 'Branch Wallet', description: "Available balance, holds and today's activity. Recharge or request a top-up from here.", side: 'bottom' }
  },
  { element: '.gs', popover: { title: 'Quick search', description: 'Jump to any page by name, from anywhere in the app.', side: 'bottom' } },
  { element: '.um__trigger', popover: { title: 'Your account', description: 'Profile, settings and sign out live here.', side: 'bottom', align: 'end' } }
];

/**
 * One-time guided walkthrough for BRANCH_MANAGER, shown the first time they land in the
 * authenticated shell. Spans every route a branch manager actually works in — dashboard, users,
 * rate master, the full booking-through-delivery Operations chain, and the branch wallet. Each
 * step with a `data.route` triggers a navigation before driver.js highlights it; the library's
 * own `waitForElement` (a MutationObserver poll, see driver.js's internal `m()`/`p()`) covers
 * the gap between route change and the API-backed widget actually rendering.
 *
 * Gate is a plain localStorage flag (per browser, following the same fail-soft `storage`
 * pattern as ThemeService/NavigationService) — no backend field for this.
 */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  maybeAutoStart(): void {
    if (storage.get(SEEN_KEY) === '1') return;
    if (!this.auth.hasAnyRole([AppRole.BRANCH_MANAGER])) return;
    this.start();
  }

  start(): void {
    const steps = BRANCH_MANAGER_STEPS;
    const goToStep = (step: TourStep | undefined) => {
      const route = step?.data?.route;
      if (route && this.router.url.split('?')[0] !== route) this.router.navigateByUrl(route);
    };

    const tour = driver({
      steps,
      showProgress: true,
      allowClose: true,
      smoothScroll: true,
      overlayOpacity: 0.55,
      stagePadding: 6,
      stageRadius: 10,
      popoverClass: 'cs-tour',
      // Generous: a couple of these steps wait on a real API-backed widget, not just
      // a route change, and the dev backend is not instant.
      waitForElement: 6000,
      onNextClick: (_el, _step, opts) => {
        goToStep(steps[(opts.index ?? -1) + 1]);
        opts.driver.moveNext();
      },
      onPrevClick: (_el, _step, opts) => {
        goToStep(steps[(opts.index ?? 1) - 1]);
        opts.driver.movePrevious();
      },
      onDestroyed: () => storage.set(SEEN_KEY, '1')
    });
    tour.drive();
  }
}
