import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const isPublicAuthRequest = /\/api\/v1\/auth\/(?:login|register(?:\/doctor)?|refresh|logout)$/
    .test(request.url.split('?')[0]);
  if (isPublicAuthRequest) {
    return next(request);
  }

  const accessToken = sessionStorage.getItem('telemed.accessToken');
  if (!accessToken || !request.url.includes('/api/v1/')) {
    return next(request);
  }
  return next(request.clone({
    setHeaders: { Authorization: `Bearer ${accessToken}` }
  }));
};
