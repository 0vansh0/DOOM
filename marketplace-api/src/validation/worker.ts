import { Worker } from 'node:worker_threads';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { loadConfig } from '../config.js';

export type PackageValidation = { manifest: Record<string, unknown>; definition: Record<string, unknown>; packageSha256: string; size: number; validatorVersion: string };
let active = 0;
const waiting: (() => void)[] = [];
async function acquire() {
  const maximum = loadConfig().VALIDATOR_CONCURRENCY;
  if (active >= maximum) await new Promise<void>(resolve => waiting.push(resolve));
  else active++;
}
function release() { const next=waiting.shift(); if(next)next(); else active--; }
export async function validatePackage(bytes: Buffer): Promise<PackageValidation> {
  const config = loadConfig();
  await acquire();
  try { return await new Promise((resolvePromise, reject) => {
    const worker = new Worker(resolve(process.cwd(), 'dist/validation/worker-entry.js'), { workerData: { bytes, id: randomUUID() },
      resourceLimits: { maxOldGenerationSizeMb: 96, maxYoungGenerationSizeMb: 24, stackSizeMb: 4 } });
    const timeout = setTimeout(() => { void worker.terminate(); reject(new Error('Package validation timed out.')); }, config.WORKER_TIMEOUT_MS);
    worker.once('message', (message: { ok: boolean; value?: PackageValidation; error?: string }) => {
      clearTimeout(timeout); void worker.terminate();
      message.ok && message.value ? resolvePromise(message.value) : reject(new Error(message.error ?? 'Package validation failed.'));
    });
    worker.once('error', error => { clearTimeout(timeout); reject(new Error(`Package validator failed: ${error.message}`)); });
    worker.once('exit', code => { if (code !== 0) { clearTimeout(timeout); reject(new Error(`Package validator exited with code ${code}.`)); } });
  }); } finally { release(); }
}
