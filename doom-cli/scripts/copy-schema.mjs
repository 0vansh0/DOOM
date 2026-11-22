import { cpSync } from 'node:fs';

cpSync(new URL('../../packages/manifest-schema/', import.meta.url),
    new URL('../dist/schema/', import.meta.url), { recursive: true });
cpSync(new URL('../../sample-mods/templates/', import.meta.url),
    new URL('../dist/schema/templates/', import.meta.url), { recursive: true });
