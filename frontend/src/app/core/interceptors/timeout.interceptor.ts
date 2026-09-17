import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, timeout, throwError, TimeoutError } from 'rxjs';

const REQUEST_TIMEOUT_MS = 30_000;

/** Long-running paths that must not use the default API timeout. */
const LONG_RUNNING_PATH = /\/bank\/imports(?:\?|$)|\/export(?:\?|$)/i;

export const timeoutInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.body instanceof FormData) {
    return next(req);
  }
  if (req.responseType === 'blob') {
    return next(req);
  }
  const path = req.url.split('?')[0];
  if (LONG_RUNNING_PATH.test(path)) {
    return next(req);
  }

  return next(req).pipe(
    timeout(REQUEST_TIMEOUT_MS),
    catchError((error: unknown) => {
      if (error instanceof TimeoutError) {
        return throwError(() => new HttpErrorResponse({
          error: { detail: 'The request took too long. Check your connection and try again.' },
          status: 0,
          statusText: 'Timeout',
          url: req.url
        }));
      }
      return throwError(() => error);
    })
  );
};
