# Courier SaaS — Admin Console (Angular 20)

Premium enterprise UI foundation for the Multi-Tenant Courier SaaS backend.

## Stack
Angular 20 (standalone, signals), Angular Material 3, Tailwind CSS, RxJS, SCSS.

## Run
```bash
npm install
npm start           # ng serve on :4200, proxies /api -> backend :8081
```
The backend must be running (`cd ../backend && DB_USERNAME=root DB_PASSWORD=... \
SERVER_PORT=8081 mvn spring-boot:run -Dspring-boot.run.profiles=local`).

Build: `npm run build` (output `dist/courier-saas-ui`).

## Architecture
```
src/app/
  core/         auth (signals) · guards · interceptors · services · models · utils · config
  shared/       12 reusable components (button, input, select, table, dialog, drawer,
                search, pagination, loader, card, status-badge, statistic-card)
  layouts/      auth-layout (split brand + form) · admin-layout (dark sidebar, header,
                breadcrumb, content, footer)
  features/     login · dashboard · company · users · roles · permissions · branch · hub · settings
  theme/ (../theme)  design tokens (palette, spacing, radius, shadows, typography)
```

## Principles
- **API only, no mock data.** Every feature has a service that calls the backend through
  `ApiService`, which unwraps the `ApiResponse` envelope in one place.
- **RBAC everywhere.** `AuthService` holds the session as signals; the sidebar filters by
  role, and `roleGuard` gates routes via `data.roles` (permission-based routing).
- **JWT with silent refresh.** `authInterceptor` attaches the bearer token; `errorInterceptor`
  rotates the pair once on a 401 and replays the request, signing out if refresh fails.
- **Lazy loading.** Every feature is code-split (`loadComponent`).
- **Dark-mode ready.** CSS custom-property tokens; `[data-theme="dark"]` overrides; a header
  toggle. Tailwind and Material 3 share the same palette.

## Backend contract
- Base `/api/v1`. Envelope `{ success, message, data, errorCode, errors[], requestId }`.
- Pagination `Page<T> { content, page, size, totalElements, totalPages, first, last, hasNext }`.
- Login `POST /auth/login` → `{ accessToken, refreshToken, roles[], ... }`; refresh `POST /auth/refresh`.
- Headers sent: `Authorization: Bearer`, `X-Request-Id`, and `X-Tenant-ID` when impersonating.
