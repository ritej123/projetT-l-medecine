import { Injectable } from '@angular/core';

const ACCESS_TOKEN_KEY = 'telemed.accessToken';

@Injectable({ providedIn: 'root' })
export class AccessTokenStore {
  getAccessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  setAccessToken(token: string): void {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  }

  clear(): void {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  }
}
