import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';

export type Role = 'patient' | 'practitioner' | 'admin';

/** Identity of the logged-in user, read from the Keycloak token. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly keycloak = inject(Keycloak);

  get displayName(): string {
    const token = this.keycloak.tokenParsed as Record<string, string> | undefined;
    return token?.['name'] ?? token?.['preferred_username'] ?? '';
  }

  get patientId(): string | null {
    return (this.keycloak.tokenParsed as Record<string, string> | undefined)?.['patient_id'] ?? null;
  }

  get practitionerId(): string | null {
    return (this.keycloak.tokenParsed as Record<string, string> | undefined)?.['practitioner_id'] ?? null;
  }

  hasRole(role: Role): boolean {
    return this.keycloak.hasRealmRole(role);
  }

  get isStaff(): boolean {
    return this.hasRole('practitioner') || this.hasRole('admin');
  }

  /** Landing page by role. */
  get homePath(): string {
    if (this.hasRole('patient')) {
      return '/book';
    }
    if (this.hasRole('practitioner')) {
      return '/agenda';
    }
    return '/patients';
  }

  logout(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
