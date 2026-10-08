import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role, SessionService } from './session.service';

/** Allows the route when the user has one of the roles, otherwise redirects to their home page. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const session = inject(SessionService);
    return roles.some((role) => session.hasRole(role)) || inject(Router).parseUrl(session.homePath);
  };
}

/** Sends "/" to the landing page of the user's role. */
export const homeRedirect: CanActivateFn = () =>
  inject(Router).parseUrl(inject(SessionService).homePath);
