import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { ParisDatePipe } from '../../core/format.pipes';
import { AppNotification } from '../../core/models';

const TITLES: Record<AppNotification['type'], string> = {
  APPOINTMENT_CONFIRMED: 'Rendez-vous confirmé',
  APPOINTMENT_REJECTED: 'Demande refusée',
  APPOINTMENT_CANCELLED: 'Rendez-vous annulé',
  APPOINTMENT_RESCHEDULED: 'Rendez-vous déplacé',
};

/** The patient's notifications (e-mails sent by notification-service), refreshed every 5 seconds. */
@Component({
  selector: 'app-notifications',
  imports: [ParisDatePipe],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css',
})
export class NotificationsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly notifications = signal<AppNotification[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly titles = TITLES;

  ngOnInit(): void {
    timer(0, 5000)
      .pipe(
        // A failed poll shows the error but keeps the polling alive.
        switchMap(() =>
          this.api.notifications().pipe(
            catchError((e) => {
              this.error.set(errorMessage(e));
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (list) => {
          this.notifications.set(list);
          this.error.set(null);
        },
      });
  }
}
