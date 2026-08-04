import { Injectable, computed, signal } from '@angular/core';

/** Global in-flight counter, driven by the loading interceptor for the top progress bar. */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly count = signal(0);
  readonly loading = computed(() => this.count() > 0);
  start() { this.count.update((n) => n + 1); }
  stop() { this.count.update((n) => Math.max(0, n - 1)); }
}
