export type AppointmentStatus = 'PENDING_VALIDATION' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';

/** Coded cancellation reasons (published in events, unlike the free-text comment). */
export type CancellationReason = 'PATIENT_REQUEST' | 'PRACTITIONER_UNAVAILABLE' | 'OTHER';

export interface Practitioner {
  id: string;
  fullName: string;
  specialty: string;
}

export interface Slot {
  start: string;
  end: string;
}

export interface Appointment {
  id: string;
  patientId: string;
  practitionerId: string;
  start: string;
  end: string;
  status: AppointmentStatus;
  reason: string | null;
  statusReason: string | null;
  statusComment: string | null;
  createdAt: string;
  updatedAt: string;
}

export type PatientStatus = 'ACTIVE' | 'INACTIVE';

export interface Patient {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  birthDate: string;
  status: PatientStatus;
  createdAt: string;
}

export type NewPatient = Omit<Patient, 'id' | 'status' | 'createdAt'>;

export interface AppNotification {
  id: string;
  patientId: string;
  appointmentId: string;
  type: 'APPOINTMENT_CONFIRMED' | 'APPOINTMENT_REJECTED' | 'APPOINTMENT_CANCELLED' | 'APPOINTMENT_RESCHEDULED';
  channel: 'EMAIL';
  status: 'SENT' | 'FAILED';
  body: string;
  createdAt: string;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}
