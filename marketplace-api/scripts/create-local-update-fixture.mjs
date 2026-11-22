import { readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readZip, writeZip } from '../../doom-cli/dist/zip.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const source = path.join(root, 'sample-mods/community/low-battery-alert.doommod');
const files = readZip(await readFile(source));
const manifest = JSON.parse(files.get('doom.json').toString('utf8'));
if (manifest.id !== 'community.lowbatteryalert' || manifest.version !== '1.0.0') throw new Error('Unexpected base package identity/version.');
manifest.version = '1.0.1';
files.set('doom.json', Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`));
const destination = path.join(tmpdir(), 'doom-13b5a-low-battery-alert-1.0.1.doommod');
await writeFile(destination, writeZip(files), { flag: 'w' });
console.log(destination);
