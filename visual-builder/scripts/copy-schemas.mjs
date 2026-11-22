import { copyFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const target = resolve(import.meta.dirname, '../public/schemas');
mkdirSync(target, { recursive: true });
for (const name of ['doom-sdk2', 'mod-v4']) {
  copyFileSync(resolve(root, `packages/manifest-schema/v2/${name}.schema.json`), resolve(target, `${name}.schema.json`));
}
copyFileSync(resolve(root, 'packages/manifest-schema/v2/registry.json'), resolve(target, 'registry.json'));
