import { Directive, ElementRef, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NAVIGATION } from '@core/navigation/navigation.config';
import { NavNode } from '@core/navigation/navigation.model';

const FALLBACK_ICON = 'article';
const MARK = 'data-page-icon';

function leaves(nodes: NavNode[], out: NavNode[] = []): NavNode[] {
  for (const n of nodes) {
    if (n.route && n.icon) out.push(n);
    if (n.children?.length) leaves(n.children, out);
  }
  return out;
}

/**
 * Gives every page title (`h1.text-h1`) inside the shell a leading icon, so no screen has to
 * remember to add one. The icon is the sidebar icon of the nav entry whose route best
 * matches the current URL (longest prefix), so "New Branch" shares Branches' icon.
 * A title that already carries its own icon is left alone.
 */
@Directive({ selector: '[appPageTitleIcon]', standalone: true })
export class PageTitleIcon implements OnInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly routes = leaves(NAVIGATION).sort((a, b) => b.route!.length - a.route!.length);
  private observer?: MutationObserver;

  ngOnInit(): void {
    this.zone.runOutsideAngular(() => {
      this.observer = new MutationObserver(() => this.decorate());
      this.observer.observe(this.el.nativeElement, { childList: true, subtree: true });
      this.decorate();
    });
  }

  ngOnDestroy(): void { this.observer?.disconnect(); }

  private iconForUrl(): string {
    const path = this.router.url.split(/[?#]/)[0];
    const hit = this.routes.find((n) => path === n.route || path.startsWith(n.route + '/'));
    return hit?.icon ?? FALLBACK_ICON;
  }

  private decorate(): void {
    const heads = this.el.nativeElement.querySelectorAll<HTMLElement>('h1.text-h1');
    if (!heads.length) return;
    const icon = this.iconForUrl();
    heads.forEach((h) => {
      const existing = h.querySelector(`[${MARK}]`);
      if (existing) {
        if (existing.textContent !== icon) existing.textContent = icon;
        return;
      }
      if (h.querySelector('mat-icon, .material-icons') || h.previousElementSibling?.matches('mat-icon, .page__head-ill')) return;
      const span = document.createElement('span');
      span.className = 'material-icons page-title-icon';
      span.setAttribute(MARK, '');
      span.setAttribute('aria-hidden', 'true');
      span.textContent = icon;
      h.prepend(span);
    });
  }
}
