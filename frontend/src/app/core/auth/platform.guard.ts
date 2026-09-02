import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { ApiService } from '../services/api.service';
import { AuthService } from './auth.service';

export const platformGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const api = inject(ApiService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  return api.get<{ platformAdmin: boolean }>('/api/v1/platform/me').pipe(
    map((response) => response.platformAdmin ? true : router.createUrlTree(['/unauthorized']))
  );
};
