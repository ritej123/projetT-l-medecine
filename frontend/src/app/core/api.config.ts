import { InjectionToken } from '@angular/core';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => window.location.hostname === 'localhost' && window.location.port === '4200'
    ? 'http://localhost:8080'
    : window.location.origin
});
