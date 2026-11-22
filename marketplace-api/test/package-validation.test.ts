import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { validatePackage } from '../src/validation/worker.js';

describe('marketplace package validator',()=>{
  it('accepts a CLI-built community package using the shared DOOM validator',async()=>{
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','community','low-battery-alert.doommod'));
    const result=await validatePackage(bytes);
    expect(result.manifest.id).toBe('community.lowbatteryalert');
    expect(result.definition.definitionVersion).toBe(4);
    expect(result.packageSha256).toMatch(/^[a-f0-9]{64}$/);
    expect(result.validatorVersion).toBe('doom-mod-cli@0.3.0');
  });
  it('preserves legacy manifest 2 / definition 1 package compatibility',async()=>{
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','releases','Battery-Glow.doommod'));
    const result=await validatePackage(bytes);
    expect(result.manifest.schemaVersion).toBe(2);
    expect(result.definition.definitionVersion).toBe(1);
  });
  it('preserves legacy manifest 2 / definition 2 package compatibility',async()=>{
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','releases','Battery-Glow-1.0.doommod'));
    const result=await validatePackage(bytes);
    expect(result.manifest.schemaVersion).toBe(2);
    expect(result.definition.definitionVersion).toBe(2);
  });
  it('rejects malformed ZIP without trusting the filename',async()=>{
    await expect(validatePackage(Buffer.from('not a zip'))).rejects.toThrow();
  });
  it('rejects packages above the compressed archive cap before parsing',async()=>{
    await expect(validatePackage(Buffer.alloc(10*1024*1024+1))).rejects.toThrow(/10 MiB/);
  });
});
