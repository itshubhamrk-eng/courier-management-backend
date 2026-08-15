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
        <div class="auth__illustration">
          <img src="assets/images/login-hero.png" alt="" class="auth__hero-img" />
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
    .auth { min-height:100vh; display:grid; grid-template-columns:1.1fr 1fr; background:var(--surface-muted); }
    .auth__brand { position:relative; padding:48px; color:var(--sidebar-fg);
      background:radial-gradient(1200px 600px at -10% -10%, var(--sidebar-hover), var(--sidebar-bg));
      display:flex; flex-direction:column; justify-content:space-between; overflow:hidden; }
    .auth__brand-top { display:flex; align-items:center; gap:12px; }
    .auth__logo { width:44px; height:44px; border-radius:16px; background:linear-gradient(155deg, var(--brand-400), var(--brand-600));
      display:grid; place-items:center; font:800 16px var(--font-display); color:#fff; box-shadow:var(--shadow-clay-sm); }
    .auth__title { font:700 20px var(--font-display); color:var(--sidebar-fg-strong); margin:0; }
    .auth__illustration { margin:8px 0; display:grid; place-items:center; }
    .auth__hero-img { width:100%; max-width:520px; height:auto; filter:drop-shadow(0 20px 30px rgba(0,0,0,.35)); }
    .auth__pitch h2 { font:700 34px/1.15 var(--font-display); letter-spacing:-.02em; color:var(--sidebar-fg-strong); max-width:16ch; }
    .auth__pitch p { color:var(--sidebar-fg); max-width:44ch; line-height:1.6; }
    .auth__foot { font-size:13px; color:var(--sidebar-section); }
    .auth__panel { display:grid; place-items:center; padding:32px; background:var(--surface-muted); }
    @media (max-width: 900px) { .auth { grid-template-columns:1fr; } .auth__brand { display:none; } }
  `]
})
export class AuthLayout { protected readonly year = new Date().getFullYear(); }
