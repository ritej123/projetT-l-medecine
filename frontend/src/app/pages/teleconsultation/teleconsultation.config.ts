import { InjectionToken } from '@angular/core';

export const TELEMED_API_BASE_URL = new InjectionToken<string>('TELEMED_API_BASE_URL', {
  providedIn: 'root',
  factory: () => window.location.hostname === 'localhost' && window.location.port === '4200'
    ? 'http://localhost:8080'
    : window.location.origin
});

export const TELEMED_ICE_SERVERS = new InjectionToken<RTCIceServer[]>('TELEMED_ICE_SERVERS', {
  providedIn: 'root',
  factory: () => [{ urls: ['stun:stun.l.google.com:19302'] }]
});
