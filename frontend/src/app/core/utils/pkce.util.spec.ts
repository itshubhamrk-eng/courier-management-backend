import { codeChallenge, randomString } from './pkce.util';

describe('pkce.util', () => {
  it('derives the S256 challenge from the RFC 7636 example', async () => {
    expect(await codeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'
    );
  });

  it('makes URL-safe random strings that differ each time', () => {
    const a = randomString();
    const b = randomString();
    expect(a).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(a).not.toBe(b);
  });
});
