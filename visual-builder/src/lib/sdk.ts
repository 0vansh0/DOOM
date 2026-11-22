import type { BuilderProject } from './project';
import { compileProject } from './project';

type BridgeResult = { ok: boolean; output?: string; error?: string; archive?: string };

export async function callSdk(command: 'validate'|'test'|'package', project: BuilderProject, scenarios?: unknown): Promise<BridgeResult> {
  const compiled = compileProject(project);
  const response = await fetch(`/__doom/${command}`, {
    method: 'POST', headers: { 'Content-Type':'application/json' },
    body: JSON.stringify({ ...compiled, scenarios })
  });
  const result = await response.json() as BridgeResult;
  if (!response.ok && !result.error) result.error = 'Local SDK rejected this request.';
  return result;
}

export function downloadBytes(filename: string, bytes: Uint8Array, type: string) {
  const url = URL.createObjectURL(new Blob([bytes], { type }));
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

export function saveProjectFile(project: BuilderProject) {
  downloadBytes(`${project.manifest.id}.doomproject.json`, new TextEncoder().encode(JSON.stringify(project, null, 2) + '\n'), 'application/json');
}

export function decodePackageBase64(base64: string): Uint8Array {
  const raw = atob(base64); const bytes = new Uint8Array(raw.length);
  for (let i=0;i<raw.length;i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}
