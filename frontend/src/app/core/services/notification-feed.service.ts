import { Injectable, computed, signal } from '@angular/core';

/** A user-facing notification item (distinct from transient toasts in NotificationService). */
export interface AppNotification {
  id: string;
  title: string;
  message: string;
  icon: string;
  /** ISO timestamp. */
  createdAt: string;
  read: boolean;
  level?: 'info' | 'success' | 'warning' | 'error';
}

/**
 * The header notification feed. Backed by a signal so the menu and the bell badge stay in
 * sync. There is no backend notifications endpoint yet, so the feed starts **empty** and the
 * menu shows an empty state — nothing is fabricated. When the endpoint lands, `load()` fills
 * `items` from the API and the rest of the UI needs no change.
 */
@Injectable({ providedIn: 'root' })
export class NotificationFeedService {
  private readonly _items = signal<AppNotification[]>([]);
  readonly items = this._items.asReadonly();
  readonly unreadCount = computed(() => this._items().filter((n) => !n.read).length);

  /** Replace the feed (e.g. from a future GET /notifications). */
  set(items: AppNotification[]): void { this._items.set(items); }

  markAllRead(): void {
    this._items.update((list) => list.map((n) => (n.read ? n : { ...n, read: true })));
  }
}
