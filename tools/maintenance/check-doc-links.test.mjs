import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { brokenLocalLinks, markdownFiles } from './check-doc-links.mjs';

test('documentation link checker follows local targets and ignores external or fenced links', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'doom-doc-links-'));
  try {
    await mkdir(path.join(root, 'docs'));
    await writeFile(path.join(root, 'docs', 'guide.md'), '[home](../README.md) [external](https://example.invalid)\n\n```md\n[ignored](missing.md)\n```');
    await writeFile(path.join(root, 'README.md'), '# test');
    await writeFile(path.join(root, 'docs', 'broken.md'), '[missing](./not-found.md)');
    const files = await markdownFiles(root);
    const broken = await brokenLocalLinks(root, files);
    assert.deepEqual(broken, [{ source: path.join('docs', 'broken.md'), target: './not-found.md' }]);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
