import { DEFAULT_CONFIG, apiUrlPattern, loadConfig, mergeConfig } from './config';

describe('runtime config', () => {
  it('merges known non-empty values over the defaults', () => {
    const config = mergeConfig({ keycloakUrl: 'https://auth.medirdv.fr/', apiUrl: '', unknown: 'x' });

    expect(config.keycloakUrl).toBe('https://auth.medirdv.fr');
    expect(config.apiUrl).toBe('/api');
    expect(config.keycloakRealm).toBe('medirdv');
    expect(mergeConfig(null)).toEqual(DEFAULT_CONFIG);
  });

  it('falls back to the defaults when /config.json is missing or invalid', async () => {
    const notFound = (() => Promise.resolve(new Response('', { status: 404 }))) as typeof fetch;
    const html = (() => Promise.resolve(new Response('<html>'))) as typeof fetch;
    const ok = (() => Promise.resolve(new Response('{"keycloakUrl":"https://kc.example.fr"}'))) as typeof fetch;

    expect(await loadConfig(notFound)).toEqual(DEFAULT_CONFIG);
    expect(await loadConfig(html)).toEqual(DEFAULT_CONFIG);
    expect((await loadConfig(ok)).keycloakUrl).toBe('https://kc.example.fr');
  });

  it('only sends the token to the API', () => {
    const relative = apiUrlPattern('/api');
    const absolute = apiUrlPattern('https://medirdv.example.fr/api');

    expect(relative.test('/api/appointments/1')).toBeTrue();
    expect(relative.test('/apix')).toBeFalse();
    expect(absolute.test('https://medirdv.example.fr/api/patients')).toBeTrue();
    expect(absolute.test('https://evil.example.fr/api/patients')).toBeFalse();
  });
});
