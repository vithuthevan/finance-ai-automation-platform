import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { apiBaseInterceptor } from './core/interceptors/api-base.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

function restoreSession() {
  const auth = inject(AuthService);
  return () => firstValueFrom(auth.bootstrapSession());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiBaseInterceptor, authInterceptor])),
    provideAnimations(),
    { provide: APP_INITIALIZER, useFactory: restoreSession, multi: true }
  ]
};
