import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActingBranchService } from '@core/services/acting-branch.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { UiAutocomplete } from '@shared/components/ui-autocomplete/ui-autocomplete';
import { SelectOption } from '@shared/components/ui-select/ui-select';

/** "Act as branch or hub" picker for users with no branch of their own. Renders nothing
 *  for anyone who has one. */
@Component({
  selector: 'app-act-as-branch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiAutocomplete],
  template: `
    @if (acting.canAct()) {
      <div class="act">
        <app-autocomplete [control]="control" label="Act as branch / hub" [options]="options()" placeholder="Search branch or hub…" />
      </div>
    }
  `,
  styles: [`.act { min-width:260px; max-width:360px; }`]
})
export class ActAsBranch implements OnInit {
  protected readonly acting = inject(ActingBranchService);
  private readonly masterData = inject(MasterDataService);

  readonly options = signal<SelectOption[]>([]);
  readonly control = new FormControl<string | null>(this.acting.acting());

  ngOnInit(): void {
    this.masterData.branchDirectory().subscribe((list) =>
      this.options.set(list
        .map((b) => ({ value: b.id, label: `${b.branchName} (${b.branchCode})${b.branchType === 'HUB' ? ' — HUB' : ''}` }))
        .sort((a, b) => a.label.localeCompare(b.label))));
    this.control.valueChanges.subscribe((id) => this.acting.set(id || null));
  }
}
