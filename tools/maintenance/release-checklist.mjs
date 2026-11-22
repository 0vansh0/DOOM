import { createHash } from 'node:crypto';
import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

async function listArtifacts(directory) {
  const result = [];
  async function walk(current) {
    let entries;
    try { entries = await readdir(current, { withFileTypes: true }); }
    catch { return; }
    for (const entry of entries) {
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) await walk(absolute);
      else if (/\.(?:apk|aab)$/i.test(entry.name) && absolute.split(path.sep).some(part => part.toLowerCase() === 'release')) result.push(absolute);
    }
  }
  await walk(directory);
  return result.sort();
}

export async function createChecklist(repository) {
  const artifactRoot = path.join(repository, 'app', 'build', 'outputs');
  const artifacts = await listArtifacts(artifactRoot);
  const lines = [
    '# Local release evidence checklist',
    '',
    `Generated: ${new Date().toISOString()}`,
    '',
    '> Generated locally from files found in the Android build output. Presence and SHA-256 are not proof of a valid signature, approved signer, tested upgrade, or distribution authorization.',
    '',
    '## Build artifacts',
    ''
  ];
  if (!artifacts.length) lines.push('No APK/AAB artifacts found under `app/build/outputs`.');
  for (const artifact of artifacts) {
    const [bytes, metadata] = await Promise.all([readFile(artifact), stat(artifact)]);
    const digest = createHash('sha256').update(bytes).digest('hex');
    lines.push('- `' + path.relative(repository, artifact).replaceAll('\\', '/') + '` — ' + metadata.size + ' bytes — SHA-256 `' + digest + '` — signature: **NOT VERIFIED BY THIS SCRIPT**');
  }
  lines.push('', '## Manual release gates', '',
    '- [ ] Approved signing identity and certificate verified with Android signing tools.',
    '- [ ] Approved HTTPS service and production configuration reviewed.',
    '- [ ] Security and marketplace moderation gates supported by current test evidence.',
    '- [ ] Same-signer application upgrade and data persistence verified.',
    '- [ ] Device, privacy, support, and legal requirements reviewed.',
    '- [ ] Explicit publication/distribution authorization recorded.',
    '', 'This report is an evidence worksheet only. It does not sign, publish, upload, or deploy artifacts.');
  return `${lines.join('\n')}\n`;
}

const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
process.stdout.write(await createChecklist(repository));
