import { InjectionToken } from '@angular/core';

/**
 * Runtime configuration, read from /config.json before the app starts. In the docker image the
 * file is written at container start from KEYCLOAK_URL / API_URL (web/docker-entrypoint.d), so
 * the same image runs locally and in Kubernetes. Missing file (ng serve) → localhost defaults.
 */
export interface AppConfig {
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
  /** API base: relative (same origin through nginx / dev proxy) or absolute. */
  apiUrl: string;
}

export const DEFAULT_CONFIG: AppConfig = {
  keycloakUrl: 'http://localhost:8280',
  keycloakRealm: 'medirdv',
  keycloakClientId: 'medirdv-web',
  apiUrl: '/api',
};

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG', {
  factory: () => DEFAULT_CONFIG,
});

/** Merges a partial runtime config over the defaults, ignoring unknown or empty values. */
export function mergeConfig(raw: unknown): AppConfig {
  const config = { ...DEFAULT_CONFIG };
  if (raw && typeof raw === 'object') {
    for (const key of Object.keys(DEFAULT_CONFIG) as (keyof AppConfig)[]) {
      const value = (raw as Record<string, unknown>)[key];
      if (typeof value === 'string' && value.trim() !== '') {
        config[key] = value.trim().replace(/\/+$/, '');
      }
    }
  }
  return config;
}

export async function loadConfig(fetchFn: typeof fetch = fetch): Promise<AppConfig> {
  try {
    const response = await fetchFn('/config.json', { cache: 'no-store' });
    return response.ok ? mergeConfig(await response.json()) : DEFAULT_CONFIG;
  } catch {
    return DEFAULT_CONFIG;
  }
}

/** Regex matching the API URLs that must receive the access token. */
export function apiUrlPattern(apiUrl: string): RegExp {
  const escaped = apiUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped}(\\/.*)?$`, 'i');
}
