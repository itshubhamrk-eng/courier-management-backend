import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { BranchResponse, CreateBranchRequest, UpdateBranchRequest } from '@core/models/branch.model';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { BranchForm } from './components/branch-form';
import { BranchCredentialsDialog } from './components/branch-credentials-dialog';
import { BranchService, Lookup } from './branch.service';

/**
 * Create Branch — wraps BranchForm in create mode, loads the manager lookup, posts.
 *
 * One POST creates the branch, its login account and its wallet. When the server generated
 * the password, the credentials dialog is opened before navigating away: that response is
 * the only place the password ever appears, so it must not be dropped on a redirect.
 */
@Component({
  selector: 'app-branch-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiLoader, BranchForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New Branch</h1><p class="text-caption">Register a booking &amp; delivery office for your company.</p></div>
      </header>
      @if (loading()) {
        <app-loader [minHeight]="280" caption="Loading…" />
      } @else {
        <app-branch-form mode="create" [managers]="managers()" [saving]="saving()" (saved)="save($event)" (cancelled)="cancel()" />
      }
    </div>
  `
})
export class BranchCreate implements OnInit {
  private readonly service = inject(BranchService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly managers = signal<Lookup[]>([]);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Branches', route: '/branches' }, { label: 'New' }]);
    this.service.managers().subscribe({
      next: (m) => { this.managers.set(m); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  save(body: CreateBranchRequest | UpdateBranchRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateBranchRequest).subscribe({
      next: (b) => {
        this.saving.set(false);
        // The wallet is provisioned after the branch commits, so it is not in this
        // response; the message says what the call actually produced.
        this.notify.success(
          `Branch created with user ${b.branchUser?.email ?? ''} as `
          + `${b.branchUser?.roleCode ?? 'BRANCH_MANAGER'}. Its wallet follows.`
        );
        this.afterCreate(b);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Branch code or name already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the branch.');
      }
    });
  }

  /**
   * A generated password is shown once, and only then; the administrator dismisses the
   * dialog before the redirect. A password they chose themselves is not echoed back and
   * needs no dialog.
   */
  private afterCreate(branch: BranchResponse): void {
    const user = branch.branchUser;
    if (!user?.temporaryPassword) {
      this.router.navigate(['/branches', branch.id]);
      return;
    }
    this.dialog
      .open(BranchCredentialsDialog, {
        data: { branchCode: branch.branchCode, branchName: branch.branchName, user },
        autoFocus: false, disableClose: true, panelClass: 'app-dialog'
      })
      .afterClosed()
      .subscribe(() => this.router.navigate(['/branches', branch.id]));
  }

  cancel(): void { this.router.navigate(['/branches']); }
}
