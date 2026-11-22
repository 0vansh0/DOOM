import { expect, it } from 'vitest';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { isMainModule } from '../src/server.js';

it('starts only its own entry point, including paths with spaces and escaped characters', () => {
  const entry = resolve('developer workspace #1', 'server.js');
  expect(isMainModule(pathToFileURL(entry).href, entry)).toBe(true);
  expect(isMainModule(pathToFileURL(entry).href, 'other.js')).toBe(false);
  expect(isMainModule(pathToFileURL(entry).href)).toBe(false);
});
