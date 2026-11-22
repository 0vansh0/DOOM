import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const cli = resolve(root, 'doom-cli/dist/cli.js');

function localSdkBridge(): Plugin {
  return {
    name: 'doom-local-sdk-bridge',
    configureServer(server) {
      server.middlewares.use('/__doom', async (req, res) => {
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        if (req.method !== 'POST' || !['/validate', '/test', '/package'].includes(req.url ?? '')) {
          res.statusCode = 404; res.end(JSON.stringify({ error: 'Unsupported local SDK request.' })); return;
        }
        const chunks: Buffer[] = []; let size = 0;
        try {
          for await (const chunk of req) {
            const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
            size += bytes.length;
            if (size > 1024 * 1024) throw new Error('Builder request exceeds 1 MiB.');
            chunks.push(bytes);
          }
          const body = JSON.parse(Buffer.concat(chunks).toString('utf8')) as any;
          const directory = mkdtempSync(join(tmpdir(), 'doom-builder-'));
          try {
            writeFileSync(join(directory, 'doom.json'), JSON.stringify(body.manifest, null, 2) + '\n', { flag: 'wx' });
            writeFileSync(join(directory, 'mod.json'), JSON.stringify(body.definition, null, 2) + '\n', { flag: 'wx' });
            const command = req.url === '/test' ? ['test', directory, writeScenarios(directory, body.scenarios)]
              : req.url === '/package' ? ['package', directory] : ['validate', directory];
            const result = spawnSync(process.execPath, [cli, ...command], { encoding: 'utf8', timeout: 15000, windowsHide: true });
            if (result.error) throw result.error;
            if (result.status !== 0) {
              res.statusCode = 422;
              res.end(JSON.stringify({ ok: false, output: result.stdout, error: result.stderr || result.stdout })); return;
            }
            if (req.url === '/package') {
              const archive = readFileSync(directory + '.doommod');
              res.end(JSON.stringify({ ok: true, output: result.stdout, archive: archive.toString('base64') })); return;
            }
            res.end(JSON.stringify({ ok: true, output: result.stdout }));
          } finally {
            rmSync(directory, { recursive: true, force: true });
            rmSync(directory + '.doommod', { force: true });
          }
        } catch (error) {
          res.statusCode = 400;
          res.end(JSON.stringify({ ok: false, error: error instanceof Error ? error.message : String(error) }));
        }
      });
    }
  };
}

function writeScenarios(directory: string, scenarios: unknown): string {
  const path = join(directory, 'scenarios.json');
  writeFileSync(path, JSON.stringify(scenarios, null, 2) + '\n', { flag: 'wx' });
  return path;
}

export default defineConfig({
  plugins: [react(), tailwindcss(), localSdkBridge()],
  server: { host: '127.0.0.1', port: 4173, strictPort: true }
});
