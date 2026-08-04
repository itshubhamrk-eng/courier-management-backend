import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateUserRequest, UpdateUserRequest } from '@core/models/user.model';
import { CompanyRole } from '@core/models/role.model';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UserForm } from './components/user-form';
import { Lookup, UserService } from './user.service';

/** Create User — wraps UserForm in create mode, forkJoins the dropdown lookups, posts. */
@Component({
  selector: 'app-user-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiLoader, UserForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New User</h1><p class="text-caption">Create a staff account. Leave the password blank for a reset-to-activate account.</p></div>
      </header>
      @if (loading()) {
        <app-loader [minHeight]="280" caption="Loading…" />
      } @else {
        <app-user-form mode="create" [roles]="roles()" [branches]="branches()" [hubs]="hubs()"
                       [managers]="managers()" [saving]="saving()" (saved)="save($event)" (cancelled)="cancel()" />
      }
    </div>
  `
})
export class UserCreate implements OnInit {
  private readonly service = inject(UserService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly roles = signal<CompanyRole[]>([]);
  readonly branches = signal<Lookup[]>([]);
  readonly hubs = signal<Lookup[]>([]);
  readonly managers = signal<Lookup[]>([]);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Users', route: '/users' }, { label: 'New' }]);
    forkJoin({ roles: this.service.roles(), branches: this.service.branches(), hubs: this.service.hubs(), managers: this.service.managers() })
      .subscribe({
        next: (l) => { this.roles.set(l.roles); this.branches.set(l.branches); this.hubs.set(l.hubs); this.managers.set(l.managers); this.loading.set(false); },
        error: () => this.loading.set(false)
      });
  }

  save(body: CreateUserRequest | UpdateUserRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateUserRequest).subscribe({
      next: (u) => {
        this.saving.set(false);
        this.notify.success(u.passwordGenerated
          ? 'User created as PENDING — reset their password to activate.'
          : 'User created and active.');
        this.router.navigate(['/users', u.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Email, username or employee code already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the user.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/users']); }
}
