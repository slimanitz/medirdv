import { Component, input, output, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CancellationReason } from '../core/models';

export interface CancelChoice {
  reasonCode: CancellationReason;
  comment: string;
}

export const CANCELLATION_REASONS: { code: CancellationReason; label: string }[] = [
  { code: 'PATIENT_REQUEST', label: 'À la demande du patient' },
  { code: 'PRACTITIONER_UNAVAILABLE', label: 'Indisponibilité du praticien' },
  { code: 'OTHER', label: 'Autre motif' },
];

/**
 * Cancellation dialog: a coded reason (sent in the cancellation event) and an optional internal
 * comment that stays in the scheduling database.
 */
@Component({
  selector: 'app-cancel-dialog',
  imports: [FormsModule],
  template: `
    <div class="backdrop" (click)="dismissed.emit()"></div>
    <div class="dialog card" role="dialog" aria-modal="true" aria-labelledby="cancel-title">
      <h2 id="cancel-title">Annuler le rendez-vous</h2>
      <p class="summary">{{ summary() }}</p>
      <form (ngSubmit)="confirm()">
        <label>
          Motif
          <select name="reasonCode" data-testid="cancel-reason" [ngModel]="reasonCode()"
                  (ngModelChange)="reasonCode.set($event)">
            @for (r of reasons; track r.code) {
              <option [value]="r.code">{{ r.label }}</option>
            }
          </select>
        </label>
        <label>
          Commentaire interne (facultatif, non transmis)
          <textarea name="comment" rows="3" maxlength="500" [ngModel]="comment()"
                    (ngModelChange)="comment.set($event)"></textarea>
        </label>
        <div class="actions">
          <button type="button" class="ghost" (click)="dismissed.emit()">Retour</button>
          <button type="submit" class="danger" data-testid="cancel-confirm">Confirmer l'annulation</button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .backdrop { position: fixed; inset: 0; background: rgba(15, 30, 40, 0.45); z-index: 50; }
    .dialog {
      position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
      width: min(440px, calc(100vw - 2rem)); z-index: 51;
      display: flex; flex-direction: column; gap: 0.75rem;
    }
    form { display: flex; flex-direction: column; gap: 0.8rem; }
    .summary { margin: 0; color: var(--text-muted); }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class CancelDialog implements OnInit {
  readonly summary = input('');
  readonly defaultReason = input<CancellationReason>('OTHER');
  readonly confirmed = output<CancelChoice>();
  readonly dismissed = output<void>();

  readonly reasons = CANCELLATION_REASONS;
  readonly reasonCode = signal<CancellationReason>('OTHER');
  readonly comment = signal('');

  ngOnInit(): void {
    this.reasonCode.set(this.defaultReason());
  }

  confirm(): void {
    this.confirmed.emit({ reasonCode: this.reasonCode(), comment: this.comment().trim() });
  }
}
