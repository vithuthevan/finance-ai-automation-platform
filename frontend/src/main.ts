import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { AppConfig } from './app/core/config/app-config';
import { AppConfigService } from './app/core/config/app-config.service';

async function loadRuntimeConfig(): Promise<Partial<AppConfig>> {
  try {
    const response = await fetch('assets/config.json', { cache: 'no-store' });
    if (!response.ok) {
      return {};
    }
    return await response.json();
  } catch {
    return {};
  }
}

loadRuntimeConfig().then((runtime) => {
  const configService = new AppConfigService();
  configService.load(runtime);
  bootstrapApplication(AppComponent, {
    ...appConfig,
    providers: [
      ...appConfig.providers,
      { provide: AppConfigService, useValue: configService }
    ]
  }).catch((err) => console.error(err));
});
