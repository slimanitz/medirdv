import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, switchMap, timer } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { addDays, groupByDay, startOfWeek, weekDays } from '../../core/dates';
import { ParisDatePipe, StatusLabelPipe } from '../../core/format.pipes';
import { Appointment, Practitioner } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { CancelChoice, CancelDialog } from '../../shared/cancel-dialog';

/** Practitioner agenda: week grid of appointments, refreshed every 10 seconds. */
@Component({
  selector: 'app-agenda',
  imports: [FormsModule, ParisDatePipe, StatusLabelPipe, CancelDialog],
  templateUrl: './agenda.html',
  styleUrl: './agenda.css',
})
export class AgendaPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new Subject<void>();

  readonly practitioners = signal<Practitioner[]>([]);
  readonly practitionerId = signal<string | null>(this.session.practitionerId);
  readonly weekStart = signal(startOfWeek(new Date()));
  readonly appointments = signal<Appointment[]>([]);
  readonly patientNames = signal(new Map<string, string>());
  readonly showInactive = signal(false);
  readonly error = signal<string | null>(null);

  readonly ownAgenda = this.session.practitionerId !== null;
  readonly days = computed(() => weekDays(this.weekStart()));
  readonly visible = computed(() =>
    this.appointments().filter(
      (a) => this.showInactive() || a.status === 'CONFIRMED' || a.status === 'PENDING_VALIDATION',
    ),
  );
  readonly byDay = computed(() => groupByDay(this.visible(), (a) => a.start, this.days()));
  readonly confirmedCount = computed(
    () => this.appointments().filter((a) => a.status === 'CONFIRMED').length,
  );

  ngOnInit(): void {
    this.api.practitioners().subscribe({
      next: (list) => {
        this.practitioners.set(list);
        if (!this.practitionerId() && list.length > 0) {
          this.practitionerId.set(list[0].id);
          this.reload$.next();
        }
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.api.patients().subscribe({
      next: (list) => this.patientNames.set(new Map(list.map((p) => [p.id, `${p.firstName} ${p.lastName}`]))),
      error: () => this.patientNames.set(new Map()),
    });
    this.reload$
      .pipe(
        switchMap(() => timer(0, 10_000)),
        switchMap(() => {
          const monday = this.weekStart();
          return this.api
            .agenda(this.practitionerId() ?? '', monday.toISOString(), addDays(monday, 7).toISOString())
            .pipe(
              catchError((e) => {
                this.error.set(errorMessage(e));
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (list) => {
          this.appointments.set(list);
          this.error.set(null);
        },
      });
    if (this.practitionerId()) {
      this.reload$.next();
    }
  }

  changePractitioner(id: string): void {
    this.practitionerId.set(id);
    this.reload$.next();
  }

  changeWeek(direction: 1 | -1 | 0): void {
    this.weekStart.set(direction === 0 ? startOfWeek(new Date()) : addDays(this.weekStart(), 7 * direction));
    this.reload$.next();
  }

  readonly cancelling = signal<Appointment | null>(null);

  cancel(appointment: Appointment, choice: CancelChoice): void {
    this.cancelling.set(null);
    this.api.cancel(appointment.id, choice.reasonCode, choice.comment).subscribe({
      next: () => this.reload$.next(),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  patientName(id: string): string {
    return this.patientNames().get(id) ?? `Patient ${id.slice(0, 8)}`;
  }
}
