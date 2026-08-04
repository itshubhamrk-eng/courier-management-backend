import { Injectable } from '@angular/core';
import { RechargeOrderResponse } from '@core/models/wallet.model';

/** What Razorpay's checkout hands back to the success handler. */
export interface RazorpaySuccess {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  prefill?: { name?: string; email?: string; contact?: string };
  theme?: { color?: string };
  handler: (r: RazorpaySuccess) => void;
  modal?: { ondismiss?: () => void };
}
interface RazorpayInstance { open(): void; on(event: string, cb: (e: unknown) => void): void; }
type RazorpayCtor = new (opts: RazorpayOptions) => RazorpayInstance;

const CHECKOUT_SRC = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Thin wrapper over Razorpay Checkout. The script is loaded lazily on first use (never at
 * startup, never bundled), so the app has no hard dependency on Razorpay being reachable.
 * `checkout(order)` resolves with the signed success payload for the backend to verify, or
 * rejects if the user dismisses or the SDK fails to load. No keys or amounts are invented —
 * everything comes from the backend's {@link RechargeOrderResponse}; the backend has no
 * `name`/`description`/`prefill` fields, so checkout shows a fixed app name.
 */
@Injectable({ providedIn: 'root' })
export class RazorpayService {
  private loading?: Promise<void>;

  /** Whether checkout.js has already been injected and is ready. */
  get ready(): boolean { return typeof (window as unknown as { Razorpay?: unknown }).Razorpay === 'function'; }

  /** Open Razorpay Checkout for a backend-issued order. Resolves with the success payload. */
  checkout(order: RechargeOrderResponse, appName: string): Promise<RazorpaySuccess> {
    return this.load().then(() => new Promise<RazorpaySuccess>((resolve, reject) => {
      const Ctor = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay;
      if (!Ctor) { reject(new Error('Razorpay SDK unavailable.')); return; }
      const rzp = new Ctor({
        key: order.publicKey,
        amount: order.amountMinor,
        currency: order.currency,
        name: appName,
        description: `Wallet recharge — ${order.walletNumber}`,
        order_id: order.orderId,
        theme: { color: '#4f46e5' },
        handler: (r) => resolve(r),
        modal: { ondismiss: () => reject(new Error('DISMISSED')) }
      });
      rzp.on('payment.failed', (e: unknown) => reject(e));
      rzp.open();
    }));
  }

  private load(): Promise<void> {
    if (this.ready) return Promise.resolve();
    if (this.loading) return this.loading;
    this.loading = new Promise<void>((resolve, reject) => {
      const el = document.createElement('script');
      el.src = CHECKOUT_SRC;
      el.async = true;
      el.onload = () => resolve();
      el.onerror = () => { this.loading = undefined; reject(new Error('Could not load Razorpay checkout.')); };
      document.head.appendChild(el);
    });
    return this.loading;
  }
}
