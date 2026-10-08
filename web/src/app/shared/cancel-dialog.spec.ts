import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CancelChoice, CancelDialog } from './cancel-dialog';

describe('CancelDialog', () => {
  let fixture: ComponentFixture<CancelDialog>;

  beforeEach(() => {
    fixture = TestBed.createComponent(CancelDialog);
    fixture.componentRef.setInput('defaultReason', 'PRACTITIONER_UNAVAILABLE');
    fixture.componentRef.setInput('summary', 'Alice Durand · lundi');
    fixture.detectChanges();
  });

  it('emits the coded reason and the trimmed internal comment', async () => {
    let choice: CancelChoice | undefined;
    fixture.componentInstance.confirmed.subscribe((c) => (choice = c));
    await fixture.whenStable();
    const select = fixture.nativeElement.querySelector('[data-testid="cancel-reason"]') as HTMLSelectElement;
    expect(select.value).toBe('PRACTITIONER_UNAVAILABLE');

    fixture.componentInstance.comment.set('  congrès  ');
    (fixture.nativeElement.querySelector('[data-testid="cancel-confirm"]') as HTMLButtonElement).click();

    expect(choice).toEqual({ reasonCode: 'PRACTITIONER_UNAVAILABLE', comment: 'congrès' });
  });

  it('offers only codes that may be chosen by users', () => {
    const options = [...fixture.nativeElement.querySelectorAll('option')].map((o: HTMLOptionElement) => o.value);
    expect(options).toEqual(['PATIENT_REQUEST', 'PRACTITIONER_UNAVAILABLE', 'OTHER']);
  });
});
