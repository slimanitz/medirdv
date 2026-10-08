import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { addDays, groupByDay, isoDay, startOfWeek, weekDays } from '../../core/dates';
import { ParisDatePipe, StatusLabelPipe } from '../../core/format.pipes';
import { Appointment, Practitioner, Slot } from '../../core/models';
import { CancelChoice, CancelDialog } from '../../shared/cancel-dialog';

/** Patient booking page: practitioner, free slot, then live status of the booking saga. */
@Component({
  selector: 'app-book',
  imports: [FormsModule, ParisDatePipe, StatusLabelPipe, CancelDialog],
  templateUrl: './book.html',
  styleUrl: './book.css',
})
export class BookPage implements OnInit {
  /** Saga status refresh period. */
  static readonly POLL_MS = 2000;

  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private polling?: Subscription;

  readonly practitioners = signal<Practitioner[]>([]);
  readonly selected = signal<Practitioner | null>(null);
  readonly weekStart = signal(startOfWeek(new Date()));
  readonly slots = signal<Slot[]>([]);
  readonly chosenSlot = signal<Slot | null>(null);
  readonly reason = signal('');
  readonly booking = signal<Appointment | null>(null);
  readonly appointments = signal<Appointment[]>([]);
  readonly loadingSlots = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly days = computed(() => weekDays(this.weekStart()));
  readonly slotsByDay = computed(() => groupByDay(this.slots(), (s) => s.start, this.days()));
  readonly canGoBack = computed(() => this.weekStart() > startOfWeek(new Date()));
  readonly practitionerNames = computed(
    () => new Map(this.practitioners().map((p) => [p.id, p.fullName] as const)),
  );

  ngOnInit(): void {
    this.api.practitioners().subscribe({
      next: (list) => this.practitioners.set(list),
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.loadAppointments();
  }

  select(practitioner: Practitioner): void {
    this.selected.set(practitioner);
    this.chosenSlot.set(null);
    this.loadSlots();
  }

  changeWeek(direction: 1 | -1): void {
    this.weekStart.set(addDays(this.weekStart(), 7 * direction));
    this.chosenSlot.set(null);
    this.loadSlots();
  }

  book(): void {
    const practitioner = this.selected();
    const slot = this.chosenSlot();
    if (!practitioner || !slot) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.book(practitioner.id, slot.start, this.reason().trim()).subscribe({
      next: (appointment) => {
        this.submitting.set(false);
        this.chosenSlot.set(null);
        this.reason.set('');
        this.booking.set(appointment);
        this.watch(appointment.id);
        this.loadSlots();
      },
      error: (e) => {
        this.submitting.set(false);
        this.error.set(errorMessage(e));
        this.loadSlots();
      },
    });
  }

  /** Polls the appointment until the saga leaves PENDING_VALIDATION. */
  watch(id: string): void {
    this.polling?.unsubscribe();
    this.polling = timer(BookPage.POLL_MS, BookPage.POLL_MS)
      .pipe(
        switchMap(() => this.api.appointment(id)),
        takeWhile((a) => a.status === 'PENDING_VALIDATION', true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (appointment) => {
          this.booking.set(appointment);
          if (appointment.status !== 'PENDING_VALIDATION') {
            this.loadAppointments();
          }
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }

  readonly cancelling = signal<Appointment | null>(null);

  cancel(appointment: Appointment, choice: CancelChoice): void {
    this.cancelling.set(null);
    this.api.cancel(appointment.id, choice.reasonCode, choice.comment).subscribe({
      next: () => {
        this.loadAppointments();
        this.loadSlots();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  isActive(appointment: Appointment): boolean {
    return appointment.status === 'CONFIRMED' || appointment.status === 'PENDING_VALIDATION';
  }

  private loadSlots(): void {
    const practitioner = this.selected();
    if (!practitioner) {
      return;
    }
    const days = this.days();
    this.loadingSlots.set(true);
    this.api.slots(practitioner.id, days[0], days[6]).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: (e) => {
        this.loadingSlots.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  private loadAppointments(): void {
    this.api.myAppointments().subscribe({
      next: (list) => this.appointments.set(list),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected readonly isoDay = isoDay;
}
