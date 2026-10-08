import { Pipe, PipeTransform } from '@angular/core';
import { AppointmentStatus } from './models';
import { PARIS } from './dates';

const LABELS: Record<AppointmentStatus, string> = {
  PENDING_VALIDATION: 'EN ATTENTE',
  CONFIRMED: 'CONFIRMÉ',
  REJECTED: 'REFUSÉ',
  CANCELLED: 'ANNULÉ',
};

/** French label of an appointment status. */
@Pipe({ name: 'statusLabel' })
export class StatusLabelPipe implements PipeTransform {
  transform(status: AppointmentStatus | null | undefined): string {
    return status ? LABELS[status] : '';
  }
}

const FORMATS = {
  time: { hour: '2-digit', minute: '2-digit' },
  day: { weekday: 'long', day: 'numeric', month: 'long' },
  short: { weekday: 'short', day: 'numeric', month: 'short' },
  full: { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' },
} satisfies Record<string, Intl.DateTimeFormatOptions>;

export type ParisDateFormat = keyof typeof FORMATS;

/** Formats an ISO instant (or YYYY-MM-DD day) in French, Europe/Paris time. */
@Pipe({ name: 'parisDate' })
export class ParisDatePipe implements PipeTransform {
  transform(value: string | null | undefined, format: ParisDateFormat = 'full'): string {
    if (!value) {
      return '';
    }
    // A plain day is interpreted at noon UTC so that it stays on the same calendar day in Paris.
    const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T12:00:00Z`) : new Date(value);
    return new Intl.DateTimeFormat('fr-FR', { ...FORMATS[format], timeZone: PARIS }).format(date);
  }
}
