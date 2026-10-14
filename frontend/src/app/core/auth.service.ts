import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AccessTokenStore } from './access-token.store';
import { AuthTokens, TelemedApiService, UserClaims } from './telemed-api.service';

const REFRESH_TOKEN_KEY = 'telemed.refreshToken';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly claims = signal<UserClaims | null>(this.readClaims());

  constructor(
    private readonly api: TelemedApiService,
    private readonly tokenStore: AccessTokenStore
  ) {}

  get isAuthenticated(): boolean {
    return this.claims() !== null;
  }

  login(email: string, password: string): Observable<AuthTokens> {
    return this.api.login(email, password).pipe(tap(tokens => this.store(tokens)));
  }

  registerPatient(email: string, password: string): Observable<AuthTokens> {
    return this.api.registerPatient(email, password).pipe(tap(tokens => this.store(tokens)));
  }

  registerDoctor(email: string, password: string, specialty: string, bio: string): Observable<void> {
    return this.api.registerDoctor(email, password, specialty, bio);
  }

  logout(): void {
    const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
    this.tokenStore.clear();
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    this.claims.set(null);
    if (refreshToken) {
      this.api.logout(refreshToken).subscribe({ error: () => undefined });
    }
  }

  private store(tokens: AuthTokens): void {
    this.tokenStore.setAccessToken(tokens.accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.claims.set(this.decodeClaims(tokens.accessToken));
  }

  private readClaims(): UserClaims | null {
    const token = this.tokenStore.getAccessToken();
    return token ? this.decodeClaims(token) : null;
  }

  private decodeClaims(token: string): UserClaims | null {
    try {
      const segment = token.split('.')[1];
      const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
      const claims = JSON.parse(atob(padded)) as UserClaims & { exp?: number; token_type?: string };
      if (claims.token_type !== 'access' || (claims.exp && claims.exp * 1000 <= Date.now())) {
        return null;
      }
      return claims;
    } catch {
      return null;
    }
  }
}
