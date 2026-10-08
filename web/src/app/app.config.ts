import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import {
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  UserActivityService,
  createInterceptorCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
  withAutoRefreshToken,
} from 'keycloak-angular';

import { routes } from './app.routes';
import { APP_CONFIG, AppConfig, apiUrlPattern } from './core/config';

export function appConfig(config: AppConfig): ApplicationConfig {
  /** The access token is only sent to our own API. */
  const apiCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
    urlPattern: apiUrlPattern(config.apiUrl),
  });

  return {
    providers: [
      { provide: APP_CONFIG, useValue: config },
      provideKeycloak({
        config: { url: config.keycloakUrl, realm: config.keycloakRealm, clientId: config.keycloakClientId },
        initOptions: {
          onLoad: 'login-required',
          pkceMethod: 'S256',
          checkLoginIframe: false,
        },
        features: [withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 3_600_000 })],
        providers: [AutoRefreshTokenService, UserActivityService],
      }),
      { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiCondition] },
      provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
      provideBrowserGlobalErrorListeners(),
      provideZoneChangeDetection({ eventCoalescing: true }),
      provideRouter(routes, withComponentInputBinding()),
    ],
  };
}
