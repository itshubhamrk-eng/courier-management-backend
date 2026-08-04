import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Toasts. Success/info/error styling via panelClass so the palette stays in SCSS. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snack = inject(MatSnackBar);

  success(msg: string) { this.show(msg, 'toast-success'); }
  info(msg: string) { this.show(msg, 'toast-info'); }
  error(msg: string) { this.show(msg, 'toast-error', 6000); }

  private show(message: string, panelClass: string, duration = 3500) {
    this.snack.open(message, 'Dismiss', {
      duration, panelClass, horizontalPosition: 'right', verticalPosition: 'top'
    });
  }
}
