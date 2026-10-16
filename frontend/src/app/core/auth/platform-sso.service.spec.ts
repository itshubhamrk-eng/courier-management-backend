import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { vi } from 'vitest';
import { PlatformSsoConfig } from '../models/auth.model';
import { ApiService } from '../services/api.service';
import { AuthService } from './auth.service';
import { PlatformSsoService } from './platform-sso.service';

const CONFIG: PlatformSsoConfig = {
  authorizationEndpoint: 'https://auth.example.com/realms/occpl/protocol/openid-connect/auth',
  tokenEndpoint: 'https://auth.example.com/realms/occpl/protocol/openid-connect/token',
  clientId: 'courier-web'
};

describe('PlatformSsoService', () => {
  const loginWithPlatform = vi.fn();
  let service: PlatformSsoService;

  beforeEach(() => {
    sessionStorage.clear();
    loginWithPlatform.mockReset().mockReturnValue(of({ accessToken: 'courier' }));
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: { get: () => of(CONFIG) } },
        { provide: AuthService, useValue: { loginWithPlatform } }
      ]
    });
    service = TestBed.inject(PlatformSsoService);
  });

  it('refuses a callback whose state does not match the one it started with', async () => {
    sessionStorage.setItem('occpl.sso.pending', JSON.stringify({ state: 'right', verifier: 'v', company: 'c' }));
    await expect(firstValueFrom(service.complete(CONFIG, 'code', 'wrong'))).rejects.toThrow(/no longer valid/);
    expect(loginWithPlatform).not.toHaveBeenCalled();
  });

  it('refuses a callback when nothing was started in this tab', async () => {
    await expect(firstValueFrom(service.complete(CONFIG, 'code', 'whatever'))).rejects.toThrow(/no longer valid/);
  });

  it('trades the code for the platform token and signs in with it for the remembered company', async () => {
    sessionStorage.setItem('occpl.sso.pending', JSON.stringify({ state: 's', verifier: 'v', company: 'company-1' }));
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ access_token: 'platform-token' }), { status: 200 })
    );

    await firstValueFrom(service.complete(CONFIG, 'the-code', 's'));

    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe(CONFIG.tokenEndpoint);
    expect(String((init as RequestInit).body)).toContain('code_verifier=v');
    expect(String((init as RequestInit).body)).toContain('code=the-code');
    expect(loginWithPlatform).toHaveBeenCalledWith('platform-token', 'company-1');
    expect(service.lastCompany).toBe('company-1');
    expect(sessionStorage.getItem('occpl.sso.pending')).toBeNull();
    fetchSpy.mockRestore();
  });

  it('says so when the platform rejects the code', async () => {
    sessionStorage.setItem('occpl.sso.pending', JSON.stringify({ state: 's', verifier: 'v', company: 'c' }));
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 400 }));
    await expect(firstValueFrom(service.complete(CONFIG, 'bad', 's'))).rejects.toThrow(/did not accept/);
    expect(loginWithPlatform).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});
