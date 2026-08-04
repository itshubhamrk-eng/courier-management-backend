import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { ConfirmData, ConfirmDialog } from './confirm-dialog';
import { ReasonData, ReasonDialog } from './reason-dialog';

/** Thin wrapper so features open a confirm without importing MatDialog wiring each time. */
@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly dialog = inject(MatDialog);

  confirm(data: ConfirmData): Observable<boolean> {
    return this.dialog.open(ConfirmDialog, { data, autoFocus: false, panelClass: 'app-dialog' })
      .afterClosed();
  }

  /**
   * A confirm that collects the reason the endpoint will demand.
   *
   * @returns the trimmed reason, or `undefined` when cancelled — never an empty string,
   *          so a caller's `if (!reason) return;` cannot be defeated by whitespace
   */
  prompt(data: ReasonData): Observable<string | undefined> {
    return this.dialog.open(ReasonDialog, { data, autoFocus: true, panelClass: 'app-dialog' })
      .afterClosed();
  }
}
