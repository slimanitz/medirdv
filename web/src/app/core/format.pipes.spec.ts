import { ParisDatePipe, StatusLabelPipe } from './format.pipes';

describe('StatusLabelPipe', () => {
  const pipe = new StatusLabelPipe();

  it('translates saga statuses to French', () => {
    expect(pipe.transform('CONFIRMED')).toBe('CONFIRMÉ');
    expect(pipe.transform('PENDING_VALIDATION')).toBe('EN ATTENTE');
    expect(pipe.transform('REJECTED')).toBe('REFUSÉ');
    expect(pipe.transform('CANCELLED')).toBe('ANNULÉ');
    expect(pipe.transform(null)).toBe('');
  });
});

describe('ParisDatePipe', () => {
  const pipe = new ParisDatePipe();

  it('formats instants in Paris time', () => {
    expect(pipe.transform('2026-10-12T07:00:00Z', 'time')).toBe('09:00');
    expect(pipe.transform('2026-11-02T08:00:00Z', 'time')).toBe('09:00');
    expect(pipe.transform('2026-10-12T07:00:00Z', 'day')).toBe('lundi 12 octobre');
  });

  it('keeps plain days on their calendar day', () => {
    expect(pipe.transform('2026-10-12', 'day')).toBe('lundi 12 octobre');
    expect(pipe.transform(undefined)).toBe('');
  });
});
