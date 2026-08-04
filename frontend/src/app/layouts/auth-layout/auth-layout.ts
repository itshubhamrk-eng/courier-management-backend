import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Split auth screen: a branded panel and the form outlet. */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <div class="auth">
      <aside class="auth__brand">
        <div class="auth__brand-top">
          <div class="auth__logo"><span>CS</span></div>
          <h1 class="auth__title">Courier SaaS</h1>
        </div>
        <div class="auth__pitch">
          <h2>Run your entire courier network from one console.</h2>
          <p>Multi-company. Role-aware. Built for scale — bookings, hubs, delivery, finance and reporting.</p>
        </div>
        <div class="auth__foot">© {{ year }} Courier SaaS. All rights reserved.</div>
      </aside>
      <main class="auth__panel"><router-outlet /></main>
    </div>
  `,
  styles: [`
    .auth { min-height:100vh; display:grid; grid-template-columns:1.1fr 1fr; }
    .auth__brand { position:relative; padding:48px; color:#e2e8f0;
      background:radial-gradient(1200px 600px at -10% -10%, #1e293b, var(--sidebar-bg));
      display:flex; flex-direction:column; justify-content:space-between; }
    .auth__brand-top { display:flex; align-items:center; gap:12px; }
    .auth__logo { width:44px; height:44px; border-radius:12px; background:var(--brand-600);
      display:grid; place-items:center; font:800 16px var(--font-sans); color:#fff; }
    .auth__title { font:700 20px var(--font-sans); color:#fff; margin:0; }
    .auth__pitch h2 { font:700 34px/1.15 var(--font-sans); letter-spacing:-.02em; color:#fff; max-width:16ch; }
    .auth__pitch p { color:#94a3b8; max-width:44ch; line-height:1.6; }
    .auth__foot { font-size:13px; color:#64748b; }
    .auth__panel { display:grid; place-items:center; padding:32px; background:var(--surface-muted); }
    @media (max-width: 900px) { .auth { grid-template-columns:1fr; } .auth__brand { display:none; } }
  `]
})
export class AuthLayout { protected readonly year = new Date().getFullYear(); }
