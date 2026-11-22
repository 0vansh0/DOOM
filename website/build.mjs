import { cp, mkdir, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(root, 'dist');
await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
for (const file of ['index.html', 'privacy.html', 'support.html', 'styles.css', 'favicon.svg', 'menu.js', 'site.js']) {
  await cp(path.join(root, file), path.join(out, file));
}
const examplesOut = path.join(out, 'examples');
await mkdir(examplesOut, { recursive: true });
for (const id of ['battery-guardian', 'battery-glow', 'edge-aura']) {
  await cp(
    path.resolve(root, `../sample-mods/community-examples/${id}/mod.json`),
    path.join(examplesOut, `${id}.json`)
  );
}
const docsOut = path.join(out, 'docs');
await mkdir(docsOut, { recursive: true });
await cp(path.resolve(root, '../docs/developers/QUICKSTART.md'), path.join(docsOut, 'developers-quickstart.md'));
await cp(path.resolve(root, '../docs/beta/DEVICE_COMPATIBILITY_MATRIX.md'), path.join(docsOut, 'device-compatibility.md'));
console.log(`Static site built to ${out}`);
