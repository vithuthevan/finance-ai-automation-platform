import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AppConfigService } from '../config/app-config.service';
import { resolveApiUrl } from '../config/app-config';

export const apiBaseInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const config = inject(AppConfigService);
  return next(req.clone({ url: resolveApiUrl(config.apiBaseUrl, req.url) }));
};
