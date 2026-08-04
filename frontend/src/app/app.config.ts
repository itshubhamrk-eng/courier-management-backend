import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';
import { authInterceptor } from '@core/interceptors/auth.interceptor';
import { requestIdInterceptor } from '@core/interceptors/request-id.interceptor';
import { loadingInterceptor } from '@core/interceptors/loading.interceptor';
import { errorInterceptor } from '@core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })
    ),
    // Order matters: request-id first, then auth header, then loading, then error (outermost catch).
    provideHttpClient(withInterceptors([
      requestIdInterceptor, authInterceptor, loadingInterceptor, errorInterceptor
    ])),
    provideAnimationsAsync()
  ]
};
