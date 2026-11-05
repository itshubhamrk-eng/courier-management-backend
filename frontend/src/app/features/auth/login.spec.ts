import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '@core/auth/auth.service';
import { NotificationService } from '@core/services/notification.service';
import { Login } from './login';

describe('Login error messages', () => {
  const login = vi.fn();

  function submitWith(err: HttpErrorResponse) {
    login.mockReturnValue(throwError(() => err));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } }
      ]
    });
    const f = TestBed.createComponent(Login);
    f.componentInstance.form.patchValue({ email: 'a@b.com', password: 'Password@1234' });
    f.componentInstance.submit();
    f.detectChanges();
    return f;
  }

  beforeEach(() => login.mockReset());

  it('shows the geo restriction message for GEO_RESTRICTED (403)', () => {
    const f = submitWith(new HttpErrorResponse({
      status: 403, error: { errorCode: 'GEO_RESTRICTED', message: 'Sign-in is not available from your location.' }
    }));
    expect(f.componentInstance.error()).toBe('Sign-in is not available from your location.');
  });

  it('uses the geo message even if the server text is missing', () => {
    const f = submitWith(new HttpErrorResponse({ status: 403, error: { errorCode: 'GEO_RESTRICTED' } }));
    expect(f.componentInstance.error()).toBe('Sign-in is not available from your location.');
  });

  it('explains rate limiting for RATE_LIMIT_EXCEEDED', () => {
    const f = submitWith(new HttpErrorResponse({ status: 429, error: { errorCode: 'RATE_LIMIT_EXCEEDED', message: 'Too many requests' } }));
    expect(f.componentInstance.error()).toBe('Too many sign-in attempts. Please wait a few minutes and try again.');
  });

  it('falls back to the server message, then the generic text, for other errors', () => {
    expect(submitWith(new HttpErrorResponse({ status: 401, error: { message: 'Invalid email or password' } }))
      .componentInstance.error()).toBe('Invalid email or password');
    TestBed.resetTestingModule();
    expect(submitWith(new HttpErrorResponse({ status: 500 })).componentInstance.error())
      .toBe('Invalid credentials. Please try again.');
  });
});
