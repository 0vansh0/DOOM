import { describe, expect, it } from 'vitest';
import { resolveMarketplaceApiBase } from './api';

describe('marketplace portal endpoint policy', () => {
  it('uses a local HTTP endpoint only in development', () => {
    expect(resolveMarketplaceApiBase(undefined, true)).toBe('http://127.0.0.1:8787');
    expect(resolveMarketplaceApiBase(undefined, false)).toBe('');
    expect(resolveMarketplaceApiBase('http://127.0.0.1:8787', true)).toBe('http://127.0.0.1:8787');
  });

  it('requires HTTPS and a non-placeholder origin outside development', () => {
    expect(resolveMarketplaceApiBase('https://api.example.com/v1/', false)).toBe('https://api.example.com/v1');
    expect(() => resolveMarketplaceApiBase('http://api.example.com', false)).toThrow(/HTTPS/);
    expect(() => resolveMarketplaceApiBase('https://localhost:8787', false)).toThrow(/localhost/);
    expect(() => resolveMarketplaceApiBase('https://marketplace.doom.invalid', false)).toThrow(/reserved/);
  });
});
