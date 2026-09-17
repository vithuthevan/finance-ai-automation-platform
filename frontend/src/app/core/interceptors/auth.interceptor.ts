import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

const AUTH_PATH = /\/api\/v1\/auth\/(login|register|refresh|logout|forgot-password|reset-password|verify-email)/;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('assets/config.json')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const token = auth.session()?.accessToken;
  let authedReq = req.clone({ withCredentials: true });
  if (token) {
    authedReq = authedReq.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(authedReq).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      if (AUTH_PATH.test(req.url) || req.headers.has('X-Auth-Retry')) {
        return throwError(() => error);
      }

      return auth.refreshSession().pipe(
        switchMap((session) =>
          next(req.clone({
            withCredentials: true,
            setHeaders: {
              Authorization: `Bearer ${session.accessToken}`,
              'X-Auth-Retry': '1'
            }
          }))
        ),
        catchError(() => {
          auth.clearSessionAndRedirect();
          return throwError(() => error);
        })
      );
    })
  );
};
