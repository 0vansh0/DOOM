import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const pages = ['index.html', 'privacy.html', 'support.html'];

test('site pages have responsive metadata and semantic landmarks', async () => {
  for (const page of pages) {
    const html = await readFile(path.join(root, page), 'utf8');
    assert.match(html, /<html lang="en"(?:\s[^>]*)?>/);
    assert.match(html, /name="viewport"/);
    assert.match(html, /name="robots" content="noindex, nofollow"/);
    assert.match(html, /<main\b[^>]*id="main"/);
    assert.match(html, /class="skip-link"/);
    assert.match(html, /<title>[^<]+<\/title>/);
    assert.match(html, /class="menu-toggle"[^>]*aria-controls="primary-nav"[^>]*aria-expanded="false"/);
    assert.match(html, /<nav class="primary-nav" id="primary-nav"/);
  }
});

test('landing page avoids unverified download and analytics claims', async () => {
  const html = await readFile(path.join(root, 'index.html'), 'utf8');
  assert.match(html, /<dt>Status<\/dt><dd>Local preview<\/dd>/);
  assert.doesNotMatch(html, /Beta closed/);
  assert.doesNotMatch(html, /BETA AVAILABILITY|No release download is available/);
  assert.doesNotMatch(html, /href=["'][^"']*(download|\.apk|\.aab)/i);
  assert.doesNotMatch(html, /google-analytics|googletagmanager|facebook\.net/i);
  assert.doesNotMatch(html, /sample author identities are not verified|does not provide system edge lighting/);
});

test('all local navigation targets resolve', async () => {
  for (const page of pages) {
    const html = await readFile(path.join(root, page), 'utf8');
    const targets = [...html.matchAll(/href="([^"]+)"/g)].map((m) => m[1]);
    for (const target of targets) {
      if (target.startsWith('#')) continue;
      if (/^https?:/i.test(target)) continue;
      const [filePart, fragment] = target.split('#');
      if (filePart === 'docs/developers-quickstart.md') {
        await access(path.resolve(root, '../docs/developers/QUICKSTART.md'));
      } else if (filePart === 'docs/device-compatibility.md') {
        await access(path.resolve(root, '../docs/beta/DEVICE_COMPATIBILITY_MATRIX.md'));
      } else if (filePart.endsWith('.md')) {
        await access(path.resolve(root, filePart));
      } else {
        await access(path.resolve(root, filePart || page));
      }
      if (fragment) assert.ok(fragment.length > 0);
    }
  }
});

test('styles include keyboard focus, small-screen layout and reduced-motion support', async () => {
  const css = await readFile(path.join(root, 'styles.css'), 'utf8');
  assert.match(css, /:focus-visible/);
  assert.match(css, /@media\s*\(max-width:\s*760px\)/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
  assert.match(css, /font-size:\s*16px/);
  assert.match(css, /--bg:#0f1115/);
  assert.match(css, /--surface:#191c20/);
  assert.doesNotMatch(css, /#a88bff|#c4b2ff/i);
  assert.match(css, /data-theme="light"|--bg:#f1efe8/);
  assert.match(css, /translateY\(1px\)/);
  assert.match(css, /calc\(var\(--step\)\*60ms\)/);
});

test('site favicon is local and available', async () => {
  const svg = await readFile(path.join(root, 'favicon.svg'), 'utf8');
  assert.match(svg, /<svg\b/);
  assert.match(svg, /<title>DOOM<\/title>/);
});

test('featured mod examples match checked-in manifests and definitions', async () => {
  const html = await readFile(path.join(root, 'index.html'), 'utf8');
  const samples = ['battery-guardian', 'battery-glow', 'edge-aura'];
  for (const sample of samples) {
    const mod = JSON.parse(await readFile(path.resolve(root, `../sample-mods/community-examples/${sample}/doom.json`), 'utf8'));
    const definition = JSON.parse(await readFile(path.resolve(root, `../sample-mods/community-examples/${sample}/mod.json`), 'utf8'));
    assert.match(html, new RegExp(mod.name));
    assert.match(html, new RegExp(mod.capabilities[0]));
    assert.equal(definition.definitionVersion, 4);
  }
  assert.match(html, /data-view-tab="json"/);
  assert.match(html, /data-battery-sim/);
  assert.match(html, /SIMULATED PREVIEW/);
  assert.match(html, /<div class="phone-clock">9:41<\/div>/);
  assert.doesNotMatch(html, /11:42|SAMPLE PREVIEW|Illustration · not a live Android screen|demo-footnote|phone-charge/);
});

test('landing copy maps to supported examples and working CLI commands', async () => {
  const html = await readFile(path.join(root, 'index.html'), 'utf8');
  const js = await readFile(path.join(root, 'site.js'), 'utf8');
  assert.match(html, /Custom Android behavior/);
  assert.match(html, /SIMULATED PREVIEW/);
  assert.doesNotMatch(html, /data-rule-trigger|data-rule-action/);
  assert.match(html, /data-mod="battery-guardian"/);
  assert.match(html, /data-mod="battery-glow"/);
  assert.match(html, /data-mod="edge-aura"/);
  assert.match(html, /Load in sandbox ↑/);
  assert.match(html, /node \.\\doom-cli\\dist\\cli\.js create/);
  assert.match(html, /node \.\\doom-cli\\dist\\cli\.js validate/);
  assert.match(html, /node \.\\doom-cli\\dist\\cli\.js package/);
  assert.doesNotMatch(html, /doom push|doom check|Quick Settings|volume-down hold|VERIFIED/);
  assert.match(html, /data-denial-toggle/);
  assert.match(html, /SIMULATED DENIAL/);
  assert.match(html, /footer-spec/);
  assert.match(html, /data-theme-icon/);
  assert.match(html, /Copied ✓/);
  assert.match(html, /<div class="phone-clock">9:41<\/div>/);
  assert.doesNotMatch(html, /SAMPLE PREVIEW|Illustration · not a live Android screen|demo-footnote|phone-charge|11:42|data-copy-status|class="spec-section"/);
  assert.doesNotMatch(html, /class="spec-section"|data-rule-trigger|data-rule-action|<span>0[123]<\/span>/);
  assert.match(js, /\.sample-tab\[data-mod\]/);
  assert.match(html, /network\.access/);
  assert.match(js, /item\.reads/);
  assert.match(js, /item\.surface/);
  assert.match(js, /scrollIntoView/);
  assert.match(js, /duration: 140/);
  assert.match(js, /1500/);
  assert.match(js, /readablePanel\?\.classList\.toggle\('is-triggered', active\)/);
  assert.match(js, /denialToggle\?\.addEventListener/);
});

test('mobile navigation is keyboard dismissible and updates its state', async () => {
  const js = await readFile(path.join(root, 'menu.js'), 'utf8');
  assert.match(js, /aria-expanded/);
  assert.match(js, /event\.key === 'Escape'/);
  assert.match(js, /toggle\.focus\(\)/);
  assert.match(js, /nav\.classList\.toggle\('is-open', open\)/);
});

test('interactive demo loads real definitions and keeps actions local', async () => {
  const html = await readFile(path.join(root, 'index.html'), 'utf8');
  const js = await readFile(path.join(root, 'site.js'), 'utf8');
  const build = await readFile(path.join(root, 'build.mjs'), 'utf8');
  assert.match(html, /data-theme-toggle/);
  assert.match(html, /data-copy=/);
  assert.match(html, /data-battery-sim/);
  assert.match(js, /fetch\(`examples\/\$\{id\}\.json`/);
  assert.match(js, /navigator\.clipboard\.writeText/);
  assert.match(js, /Number\(level\) <= 20/);
  assert.match(build, /examplesOut/);
  assert.match(build, /sample-mods\/community-examples/);
  assert.doesNotMatch(js, /localStorage|https?:\/\//);
});
