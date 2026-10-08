import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './api.service';

describe('errorMessage', () => {
  it('returns the French detail of a problem response', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { title: 'Conflict', detail: "Ce créneau n'est pas disponible." },
    });

    expect(errorMessage(error)).toBe("Ce créneau n'est pas disponible.");
  });

  it('explains network and permission errors', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('injoignable');
    expect(errorMessage(new HttpErrorResponse({ status: 403 }))).toContain("pas accès");
    expect(errorMessage(new Error('boom'))).toBe('Une erreur inattendue est survenue.');
  });
});
