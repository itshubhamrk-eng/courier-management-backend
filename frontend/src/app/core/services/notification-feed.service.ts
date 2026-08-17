import { HttpContext } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { ApiService, SILENT_ERRORS } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Page } from '@core/models/page.model';
import { AppNotification as TicketNotification, NotificationType } from '@core/models/ticket.model';

/** A user-facing notification item (distinct from transient toasts in NotificationService). */
export interface AppNotification {
  id: string;
  title: string;
  message: string;
  icon: string;
  ticketId?: string | null;
  /** ISO timestamp. */
  createdAt: string;
  read: boolean;
  level?: 'info' | 'success' | 'warning' | 'error';
}

const ICON_BY_TYPE: Record<NotificationType, string> = {
  TICKET_ASSIGNED: 'assignment_ind',
  TICKET_REASSIGNED: 'sync_alt',
  TICKET_ESCALATED: 'trending_up',
  NEW_REPLY: 'chat',
  INTERNAL_UPDATE: 'edit_note',
  STATUS_CHANGED: 'autorenew',
  PRIORITY_CHANGED: 'flag',
  TICKET_RESOLVED: 'check_circle',
  TICKET_CLOSED: 'task_alt',
  TICKET_REOPENED: 'restart_alt',
  SLA_APPROACHING: 'schedule',
  SLA_BREACHED: 'report_problem'
};

const LEVEL_BY_TYPE: Record<NotificationType, 'info' | 'success' | 'warning' | 'error'> = {
  TICKET_ASSIGNED: 'info',
  TICKET_REASSIGNED: 'info',
  TICKET_ESCALATED: 'warning',
  NEW_REPLY: 'info',
  INTERNAL_UPDATE: 'info',
  STATUS_CHANGED: 'info',
  PRIORITY_CHANGED: 'warning',
  TICKET_RESOLVED: 'success',
  TICKET_CLOSED: 'success',
  TICKET_REOPENED: 'warning',
  SLA_APPROACHING: 'warning',
  SLA_BREACHED: 'error'
};

function toFeedItem(n: TicketNotification): AppNotification {
  return {
    id: n.id, title: n.title, message: n.message, ticketId: n.ticketId,
    createdAt: n.createdAt, read: n.read, icon: ICON_BY_TYPE[n.type], level: LEVEL_BY_TYPE[n.type]
  };
}

/**
 * The header notification feed. Backed by a signal so the menu and the bell badge stay in
 * sync. Polls `GET /notifications` every 60s — this platform has no websocket/SSE
 * infrastructure, so near-real-time is the honest bar. Root-scoped: constructed once, the
 * first time `NotificationMenu` (rendered only inside the authenticated `AdminLayout`) injects
 * it, so the first poll never fires against an anonymous session.
 */
@Injectable({ providedIn: 'root' })
export class NotificationFeedService {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _items = signal<AppNotification[]>([]);
  readonly items = this._items.asReadonly();
  readonly unreadCount = computed(() => this._items().filter((n) => !n.read).length);
  private readonly silent = new HttpContext().set(SILENT_ERRORS, true);

  constructor() {
    this.load();
    const handle = setInterval(() => this.load(), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(handle));
  }

  load(): void {
    this.api.get<Page<TicketNotification>>(
      API.notifications, { page: 0, size: 20, sort: 'createdAt,desc' }, this.silent
    ).subscribe({ next: (page) => this._items.set(page.content.map(toFeedItem)) });
  }

  markRead(id: string): void {
    const wasUnread = this._items().find((n) => n.id === id && !n.read);
    if (!wasUnread) return;
    this._items.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
    this.api.patch(`${API.notifications}/${id}/read`, {}, this.silent)
      .subscribe({ error: () => this._items.update((list) => list.map((n) => (n.id === id ? { ...n, read: false } : n))) });
  }

  markAllRead(): void {
    if (this.unreadCount() === 0) return;
    const previous = this._items();
    this._items.update((list) => list.map((n) => (n.read ? n : { ...n, read: true })));
    this.api.patch(`${API.notifications}/read-all`, {}, this.silent)
      .subscribe({ error: () => this._items.set(previous) });
  }
}
