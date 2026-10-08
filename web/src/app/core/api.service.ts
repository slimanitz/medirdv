import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from './config';
import {
  AppNotification,
  Appointment,
  CancellationReason,
  NewPatient,
  Patient,
  Practitioner,
  ProblemDetail,
  Slot,
} from './models';

/** Typed client of the gateway API. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(APP_CONFIG).apiUrl;

  practitioners(): Observable<Practitioner[]> {
    return this.http.get<Practitioner[]>(`${this.api}/practitioners`);
  }

  slots(practitionerId: string, from: string, to: string): Observable<Slot[]> {
    return this.http.get<Slot[]>(`${this.api}/practitioners/${practitionerId}/slots`, {
      params: { from, to },
    });
  }

  book(practitionerId: string, start: string, reason: string): Observable<Appointment> {
    return this.http.post<Appointment>(`${this.api}/appointments`, { practitionerId, start, reason });
  }

  appointment(id: string): Observable<Appointment> {
    return this.http.get<Appointment>(`${this.api}/appointments/${id}`);
  }

  myAppointments(): Observable<Appointment[]> {
    return this.http.get<Appointment[]>(`${this.api}/appointments`);
  }

  agenda(practitionerId: string, from: string, to: string): Observable<Appointment[]> {
    return this.http.get<Appointment[]>(`${this.api}/appointments`, {
      params: { practitionerId, from, to },
    });
  }

  /** The comment stays in scheduling-service; only the reason code is published in events. */
  cancel(id: string, reasonCode: CancellationReason, comment = ''): Observable<Appointment> {
    const params: Record<string, string> = { reasonCode };
    if (comment.trim()) {
      params['comment'] = comment.trim();
    }
    return this.http.delete<Appointment>(`${this.api}/appointments/${id}`, { params });
  }

  patients(): Observable<Patient[]> {
    return this.http.get<Patient[]>(`${this.api}/patients`);
  }

  createPatient(patient: NewPatient): Observable<Patient> {
    return this.http.post<Patient>(`${this.api}/patients`, patient);
  }

  deactivatePatient(id: string): Observable<Patient> {
    return this.http.post<Patient>(`${this.api}/patients/${id}/deactivate`, {});
  }

  notifications(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(`${this.api}/notifications`);
  }
}

/** Extracts the French message of a problem detail returned by the services. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'Le service est injoignable. Réessayez dans quelques instants.';
    }
    if (error.status === 403) {
      return "Vous n'avez pas accès à cette ressource.";
    }
  }
  return 'Une erreur inattendue est survenue.';
}
