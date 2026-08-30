import { Injectable } from '@angular/core';
import { AppConfig, DEFAULT_APP_CONFIG } from './app-config';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private config: AppConfig = DEFAULT_APP_CONFIG;

  get apiBaseUrl(): string {
    return this.config.apiBaseUrl || DEFAULT_APP_CONFIG.apiBaseUrl;
  }

  load(config: Partial<AppConfig> | null | undefined): void {
    this.config = {
      ...DEFAULT_APP_CONFIG,
      ...config,
      apiBaseUrl: config?.apiBaseUrl?.trim() || DEFAULT_APP_CONFIG.apiBaseUrl
    };
  }
}
