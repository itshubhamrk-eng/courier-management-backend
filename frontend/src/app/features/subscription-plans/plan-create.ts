import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateSubscriptionPlanRequest, UpdateSubscriptionPlanRequest } from '@core/models/subscription-plan.model';
import { PlanForm } from './components/plan-form';
import { SubscriptionPlanService } from './subscription-plan.service';

/** Create Subscription Plan — wraps PlanForm in create mode and POSTs. */
@Component({
  selector: 'app-plan-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PlanForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New Plan</h1>
          <p class="text-caption">Add a plan to the platform catalogue.</p></div>
      </header>
      <app-plan-form mode="create" [saving]="saving()" (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class PlanCreate implements OnInit {
  private readonly service = inject(SubscriptionPlanService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Subscription Plans', route: '/subscription-plans' }, { label: 'New' }]);
  }

  save(body: CreateSubscriptionPlanRequest | UpdateSubscriptionPlanRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateSubscriptionPlanRequest).subscribe({
      next: (p) => { this.saving.set(false); this.notify.success('Plan created.'); this.router.navigate(['/subscription-plans', p.id]); },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'A plan with that code or name already exists.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the plan.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/subscription-plans']); }
}
