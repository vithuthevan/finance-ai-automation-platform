export interface AppConfig {
  /**
   * API prefix used by the workspace.
   * Development (proxy or same-origin): `/api/v1`
   * Separate backend host: `https://api.example.com/api/v1`
   */
  apiBaseUrl: string;
}

export const DEFAULT_APP_CONFIG: AppConfig = {
  apiBaseUrl: '/api/v1'
};

export function resolveApiUrl(apiBaseUrl: string, path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  const relative = path.replace(/^\/api\/v1\/?/, '').replace(/^\//, '');
  const base = apiBaseUrl.replace(/\/$/, '');
  return relative ? `${base}/${relative}` : base;
}
