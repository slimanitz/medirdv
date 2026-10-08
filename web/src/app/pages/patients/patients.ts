import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { ParisDatePipe } from '../../core/format.pipes';
import { NewPatient, Patient } from '../../core/models';

/** Staff page: patient list, creation and deactivation. */
@Component({
  selector: 'app-patients',
  imports: [ReactiveFormsModule, ParisDatePipe],
  templateUrl: './patients.html',
  styleUrl: './patients.css',
})
export class PatientsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  readonly patients = signal<Patient[]>([]);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.pattern(/^[+0-9 .()-]{6,20}$/)]],
    birthDate: ['', Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const patient: NewPatient = { ...value, phone: value.phone.trim() || null };
    this.saving.set(true);
    this.error.set(null);
    this.api.createPatient(patient).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.form.reset();
        this.success.set(`Dossier de ${created.firstName} ${created.lastName} créé.`);
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  deactivate(patient: Patient): void {
    if (
      !confirm(
        `Désactiver le dossier de ${patient.firstName} ${patient.lastName} ? Ses rendez-vous à venir seront annulés.`,
      )
    ) {
      return;
    }
    this.api.deactivatePatient(patient.id).subscribe({
      next: () => {
        this.success.set('Dossier désactivé, les rendez-vous à venir vont être annulés.');
        this.load();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  private load(): void {
    this.api.patients().subscribe({
      next: (list) => this.patients.set(list),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
