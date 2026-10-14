import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const roleGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const claims = auth.claims();
  if (!claims) {
    return router.createUrlTree(['/connexion']);
  }

  const configuredRoles = route.data['roles'];
  const expectedRole = route.data['role'];
  const allowedRoles = Array.isArray(configuredRoles)
    ? configuredRoles.filter((role): role is string => typeof role === 'string')
    : typeof expectedRole === 'string'
      ? [expectedRole]
      : [];
  if (allowedRoles.length === 0 || allowedRoles.some(role => claims.roles.includes(role))) {
    return true;
  }

  if (claims.roles.includes('ADMIN')) {
    return router.createUrlTree(['/admin']);
  }
  if (claims.roles.includes('DOCTOR')) {
    return router.createUrlTree(['/medecin/dashboard']);
  }
  if (claims.roles.includes('PATIENT')) {
    return router.createUrlTree(['/patient']);
  }
  return router.createUrlTree(['/']);
};
