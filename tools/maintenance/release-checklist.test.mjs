import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createChecklist } from './release-checklist.mjs';

test('release checklist hashes discovered artifacts without implying signature approval', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'doom-release-check-'));
  try {
    const output = path.join(root, 'app', 'build', 'outputs', 'apk', 'release');
    await mkdir(output, { recursive: true });
    await writeFile(path.join(output, 'app-release-unsigned.apk'), 'test artifact');
    const report = await createChecklist(root);
    assert.match(report, /app\/build\/outputs\/apk\/release\/app-release-unsigned\.apk/);
    assert.match(report, /SHA-256 `[0-9a-f]{64}`/);
    assert.match(report, /NOT VERIFIED BY THIS SCRIPT/);
    assert.match(report, /does not sign, publish, upload, or deploy/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
