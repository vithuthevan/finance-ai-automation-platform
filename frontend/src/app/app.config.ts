import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { ModuleRegistry, AllCommunityModule } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { apiBaseInterceptor } from './core/interceptors/api-base.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { timeoutInterceptor } from './core/interceptors/timeout.interceptor';
import { AuthService } from './core/auth/auth.service';

ModuleRegistry.registerModules([AllCommunityModule]);

function restoreSession() {
  const auth = inject(AuthService);
  return () => firstValueFrom(auth.bootstrapSession());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiBaseInterceptor, authInterceptor, timeoutInterceptor])),
    provideAnimations(),
    provideNativeDateAdapter(),
    provideCharts(withDefaultRegisterables()),
    provideTranslateService({
      lang: 'en',
      fallbackLang: 'en',
      loader: provideTranslateHttpLoader({
        prefix: './assets/i18n/',
        suffix: '.json'
      })
    }),
    { provide: APP_INITIALIZER, useFactory: restoreSession, multi: true },
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: { appearance: 'outline', subscriptSizing: 'dynamic' }
    }
  ]
};
