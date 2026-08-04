import { Injectable, signal } from '@angular/core';

export interface Crumb { label: string; route?: string; }

/** Drives the header breadcrumb. Pages set it in their route data or on init. */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  readonly crumbs = signal<Crumb[]>([]);
  set(crumbs: Crumb[]) { this.crumbs.set(crumbs); }
}
