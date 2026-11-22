import { readFileSync } from 'node:fs';

/** Stable local API used by the marketplace validator worker. */
export { validateFiles, compressedLimit, expandedLimit, entryLimit } from './format.js';
export { readZip } from './zip.js';
export const validatorVersion = `doom-mod-cli@${(JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')) as {version:string}).version}`;
