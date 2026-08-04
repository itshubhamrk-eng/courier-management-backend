import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateRoleRequest, RoleProfile, UpdateRoleRequest } from '@core/models/role.model';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { RoleForm } from './components/role-form';
import { RoleService } from './role.service';

/**
 * Create Role — wraps RoleForm in create mode and POSTs. Doubles as the Clone flow: with a
 * `?cloneFrom=<id>` query param it fetches that role's profile and prefills the form (name,
 * type, description copied; code left blank — codes are unique per company). No backend
 * clone endpoint exists, so cloning is an honest prefilled create.
 */
@Component({
  selector: 'app-role-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiLoader, RoleForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">{{ cloning() ? 'Clone Role' : 'New Role' }}</h1>
          <p class="text-caption">
            @if (cloning()) { Create a new role starting from an existing one. } @else { Create a role for your company. }
          </p></div>
      </header>
      @if (loading()) {
        <app-loader [minHeight]="280" caption="Loading…" />
      } @else {
        <app-role-form mode="create" [prefill]="prefill()" [saving]="saving()"
                       (saved)="save($event)" (cancelled)="cancel()" />
      }
    </div>
  `
})
export class RoleCreate implements OnInit {
  private readonly service = inject(RoleService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly prefill = signal<RoleProfile | null>(null);
  readonly cloning = signal(false);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Roles', route: '/roles' }, { label: 'New' }]);
    const cloneFrom = this.route.snapshot.queryParamMap.get('cloneFrom');
    if (!cloneFrom) return;
    this.cloning.set(true);
    this.loading.set(true);
    this.breadcrumb.set([{ label: 'Roles', route: '/roles' }, { label: 'Clone' }]);
    this.service.get(cloneFrom).subscribe({
      next: (r) => { this.prefill.set(r); this.loading.set(false); },
      error: () => { this.loading.set(false); this.notify.error('Could not load the role to clone; starting blank.'); }
    });
  }

  save(body: CreateRoleRequest | UpdateRoleRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateRoleRequest).subscribe({
      next: (r) => { this.saving.set(false); this.notify.success('Role created.'); this.router.navigate(['/roles', r.id]); },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'A role with that code or name already exists.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the role.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/roles']); }
}
