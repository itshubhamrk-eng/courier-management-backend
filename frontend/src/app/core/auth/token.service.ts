import { Injectable } from '@angular/core';
import { storage } from '../utils/storage.util';
import { isExpired } from '../utils/jwt.util';
import { environment } from '@env/environment';

const ACCESS = 'cs.access';
const REFRESH = 'cs.refresh';
const COMPANY = 'cs.company';

/** Token store. The only thing that knows where tokens live. */
@Injectable({ providedIn: 'root' })
export class TokenService {
  get accessToken(): string | null { return storage.get(ACCESS); }
  get refreshToken(): string | null { return storage.get(REFRESH); }
  /** Impersonated company for a platform admin; sent as X-Company-ID when present. */
  get companyId(): string | null { return storage.get(COMPANY); }

  setTokens(access: string, refresh: string): void {
    storage.set(ACCESS, access);
    storage.set(REFRESH, refresh);
  }
  setCompany(companyId: string | null): void {
    companyId ? storage.set(COMPANY, companyId) : storage.remove(COMPANY);
  }
  clear(): void {
    storage.remove(ACCESS);
    storage.remove(REFRESH);
    storage.remove(COMPANY);
  }

  hasValidAccess(): boolean {
    const t = this.accessToken;
    return !!t && !isExpired(t, environment.accessTokenSkewSeconds);
  }
}
