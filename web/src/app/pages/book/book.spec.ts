import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Appointment, Practitioner } from '../../core/models';
import { BookPage } from './book';

describe('BookPage', () => {
  const martin: Practitioner = {
    id: 'p1',
    fullName: 'Dr Claire Martin',
    specialty: 'Médecine générale',
  };
  const pending: Appointment = {
    id: 'a1',
    patientId: 'alice',
    practitionerId: 'p1',
    start: '2026-10-12T07:00:00Z',
    end: '2026-10-12T07:30:00Z',
    status: 'PENDING_VALIDATION',
    reason: null,
    statusReason: null,
    statusComment: null,
    createdAt: '2026-10-08T10:00:00Z',
    updatedAt: '2026-10-08T10:00:00Z',
  };

  let fixture: ComponentFixture<BookPage>;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', [
      'practitioners',
      'slots',
      'book',
      'appointment',
      'myAppointments',
      'cancel',
    ]);
    api.practitioners.and.returnValue(of([martin]));
    api.myAppointments.and.returnValue(of([]));
    api.slots.and.returnValue(of([{ start: pending.start, end: pending.end }]));
    api.book.and.returnValue(of(pending));

    TestBed.configureTestingModule({
      imports: [BookPage],
      providers: [{ provide: ApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(BookPage);
    fixture.detectChanges();
  });

  it('lists practitioners', () => {
    expect(fixture.nativeElement.textContent).toContain('Dr Claire Martin');
  });

  it('polls the booking every 2 seconds until it is confirmed', fakeAsync(() => {
    api.appointment.and.returnValues(of(pending), of({ ...pending, status: 'CONFIRMED' }));
    const page = fixture.componentInstance;
    page.select(martin);
    page.chosenSlot.set({ start: pending.start, end: pending.end });

    page.book();
    fixture.detectChanges();
    const status = () =>
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="booking-status"]')!.textContent!.trim();
    expect(api.book).toHaveBeenCalledWith('p1', pending.start, '');
    expect(status()).toBe('EN ATTENTE');

    tick(BookPage.POLL_MS);
    fixture.detectChanges();
    expect(status()).toBe('EN ATTENTE');

    tick(BookPage.POLL_MS);
    fixture.detectChanges();
    expect(status()).toBe('CONFIRMÉ');

    tick(BookPage.POLL_MS * 3);
    expect(api.appointment).toHaveBeenCalledTimes(2);
  }));
});
