import { parentPort, workerData } from 'node:worker_threads';
import { createHash } from 'node:crypto';
import { readZip, validateFiles, validatorVersion } from 'doom-mod-cli/marketplace';

type Input = { bytes: Uint8Array; id: string };
const input = workerData as Input;
try {
  const bytes = Buffer.from(input.bytes);
  if (bytes.length > 10 * 1024 * 1024) throw new Error('Compressed package exceeds 10 MiB.');
  const files = readZip(bytes);
  const result = validateFiles(files);
  const manifest = result.manifest as Record<string, unknown>;
  const definition = result.definition as Record<string, unknown>;
  const manifestVersion=Number(manifest.schemaVersion), definitionVersion=Number(definition.definitionVersion);
  const compatible=(manifestVersion===2&&(definitionVersion===1||definitionVersion===2))||(manifestVersion===3&&(definitionVersion===3||definitionVersion===4));
  if (!compatible) throw new Error('Unsupported manifest/definition version pairing. Supported pairs are Manifest 2 with Definition 1–2 and Manifest 3 with Definition 3–4.');
  parentPort?.postMessage({ ok: true, value: { manifest, definition, packageSha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length,
    validatorVersion } });
} catch (error) { parentPort?.postMessage({ ok: false, error: error instanceof Error ? error.message : 'Package validation failed.' }); }
