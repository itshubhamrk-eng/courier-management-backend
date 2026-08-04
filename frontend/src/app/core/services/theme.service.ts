import { Injectable, effect, signal } from '@angular/core';
import { storage } from '../utils/storage.util';

type Mode = 'light' | 'dark';
const KEY = 'cs.theme';

/** Light/dark toggle. Stamps data-theme on <html>; tokens do the rest. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<Mode>((storage.get(KEY) as Mode) ?? 'light');

  constructor() {
    effect(() => {
      const m = this.mode();
      document.documentElement.setAttribute('data-theme', m);
      storage.set(KEY, m);
    });
  }
  toggle() { this.mode.update((m) => (m === 'light' ? 'dark' : 'light')); }
}
