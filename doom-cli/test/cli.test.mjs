import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { readZip, writeZip } from '../dist/zip.js';
import { validateFiles } from '../dist/format.js';

const cli = fileURLToPath(new URL('../dist/cli.js', import.meta.url));
const source = new URL('../../packages/manifest-schema/fixtures/battery-glow/', import.meta.url);
const release = new URL('../../sample-mods/releases/Battery-Glow.doommod', import.meta.url);
const call = (cwd, ...args) => spawnSync(process.execPath, [cli, ...args], { cwd, encoding: 'utf8' });

function temporary(task) {
    const path = mkdtempSync(join(tmpdir(), 'doom-cli-'));
    try { return task(path); }
    finally { rmSync(path, { recursive: true, force: true }); }
}

test('create a project, validate, package and inspect', () => temporary(root => {
    for (const args of [['create', 'amber-charge'], ['validate', 'amber-charge'], ['package', 'amber-charge']]) {
        const result = call(root, ...args);
        assert.equal(result.status, 0, result.stderr);
    }
    const result = call(root, 'inspect', 'amber-charge.doommod');
    assert.equal(result.status, 0, result.stderr);
    const output = JSON.parse(result.stdout);
    assert.equal(output.manifest.id, 'local.ambercharge');
    assert.match(output.sha256, /^[0-9a-f]{64}$/);
    assert.equal(output.files.length, 2);
}));

test('create, simulate and package each expanded public developer template', () => temporary(root => {
    const names = ['basic-event-driven', 'battery-triggered', 'overlay-based', 'conditional-workflow',
        'multi-action-workflow', 'dynamic-settings', 'optional-capability', 'shizuku-safe'];
    for (const template of names) {
        const project = 'sample-' + template;
        const created = call(root, 'create', project, '--template', template);
        assert.equal(created.status, 0, `${template}: ${created.stderr}`);
        assert.ok(readFileSync(join(root, project, 'README.md'), 'utf8').length > 20);
        assert.ok(readFileSync(join(root, project, 'test-scenario.json'), 'utf8').length > 20);
        assert.equal(call(root, 'validate', project).status, 0, template);
        const simulated = call(root, 'test', project, join(project, 'test-scenario.json'));
        assert.equal(simulated.status, 0, `${template}: ${simulated.stderr}${simulated.stdout}`);
        const packaged = call(root, 'package', project);
        assert.equal(packaged.status, 0, `${template}: ${packaged.stderr}`);
        const inspected = call(root, 'inspect', project + '.doommod');
        assert.equal(inspected.status, 0, `${template}: ${inspected.stderr}`);
        const archive = JSON.parse(inspected.stdout);
        assert.match(archive.sha256, /^[0-9a-f]{64}$/);
        assert.deepEqual(archive.files.map(file => file.name).sort(), ['doom.json', 'mod.json']);
        if (template === 'shizuku-safe') assert.doesNotMatch(readFileSync(join(root, project, 'mod.json'), 'utf8'), /shizuku\.|shell/i);
    }
}));

test('help documents the test command and usage errors have a distinct exit code', () => {
    const help = call(process.cwd(), '--help');
    assert.equal(help.status, 0);
    assert.match(help.stdout, /doom test <project-directory> <scenario\.json>/);
    assert.equal(call(process.cwd(), 'create', '--help').status, 0);
    const invalid = call(process.cwd(), 'unknown', 'value');
    assert.equal(invalid.status, 2);
});

test('desktop test runner evaluates community scenarios and reports failed assertions', () => {
    const root = new URL('../../sample-mods/community/', import.meta.url);
    const scenarios = new URL('../../sample-mods/community-tests/', import.meta.url);
    for (const name of ['low-battery-alert', 'charging-companion', 'custom-glow-theme']) {
        const result = call(process.cwd(), 'test', fileURLToPath(new URL(name + '/', root)),
            fileURLToPath(new URL(name + '.json', scenarios)));
        assert.equal(result.status, 0, result.stderr + result.stdout);
        assert.match(result.stdout, /passed, 0 failed/);
    }
    temporary(path => {
        const project = join(path, 'sample');
        cpSync(new URL('../../sample-mods/community/custom-glow-theme/', import.meta.url), project, { recursive: true });
        const scenario = join(project, 'bad-scenarios.json');
        writeFileSync(scenario, JSON.stringify({ formatVersion: 1, scenarios: [{
            id: 'deliberately-wrong', event: { event: 'mod.manual.trigger' },
            expected: { actionSequence: ['wrong'], suppressedActions: [] }
        }] }));
        const result = call(path, 'test', project, scenario);
        assert.equal(result.status, 1, result.stdout + result.stderr);
        assert.match(result.stdout, /1 failed/);
    });
});

test('desktop runner preserves the legacy V3 threshold condition behavior', () => {
    const result = call(process.cwd(), 'test', fileURLToPath(new URL('../../sample-mods/sdk2/low-battery-card/', import.meta.url)),
        fileURLToPath(new URL('../../sample-mods/community-tests/legacy-v3-low-battery.json', import.meta.url)));
    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.match(result.stdout, /1 passed, 0 failed/);
});

test('release package is reproducible byte for byte', () => temporary(root => {
    cpSync(source, join(root, 'battery'), { recursive: true });
    const result = call(root, 'package', 'battery');
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(readFileSync(join(root, 'battery.doommod')), readFileSync(release));
}));

test('reject unknown capability and invalid settings range', () => temporary(root => {
    const project = join(root, 'battery');
    cpSync(source, project, { recursive: true });
    const manifest = join(project, 'doom.json');
    const original = readFileSync(manifest, 'utf8');
    writeFileSync(manifest, original.replace('battery.read', 'network.read'));
    assert.notEqual(call(root, 'validate', 'battery').status, 0);
    writeFileSync(manifest, original);
    const definition = join(project, 'mod.json');
    writeFileSync(definition, readFileSync(definition, 'utf8').replace('"min": 10', '"min": 50'));
    assert.notEqual(call(root, 'package', 'battery').status, 0);
}));

test('reject duplicate JSON keys, traversal and deceptive asset files', () => {
    const files = readZip(readFileSync(release));
    const json = files.get('doom.json').toString('utf8');
    files.set('doom.json', Buffer.from(json.replace('"schemaVersion": 2', '"schemaVersion": 2, "schemaVersion": 2')));
    assert.throws(() => validateFiles(files), /Duplicate/);
    files.set('doom.json', Buffer.from(json));
    files.set('assets/glow.png', Buffer.from('fake'));
    assert.throws(() => validateFiles(files), /PNG/);
    files.set('../file', Buffer.from('invalid'));
    assert.throws(() => readZip(writeZip(files)), /entry path/);
});

test('validates v2 event bindings and rejects references without capability approval declarations', () => {
    const release = new URL('../../sample-mods/releases/Runtime-Glow.doommod', import.meta.url);
    const files = readZip(readFileSync(release));
    assert.equal(validateFiles(files).manifest.id, 'community.runtimeglow');
    const original = files.get('doom.json').toString('utf8');
    const manifest = JSON.parse(original);
    manifest.capabilities = manifest.capabilities.filter(id => id !== 'ui.preview');
    files.set('doom.json', Buffer.from(JSON.stringify(manifest)));
    assert.throws(() => validateFiles(files), /undeclared capability/);
    files.set('doom.json', Buffer.from(original));
    const definition = JSON.parse(files.get('mod.json'));
    definition.triggers[0].effect = 'missing';
    files.set('mod.json', Buffer.from(JSON.stringify(definition)));
    assert.throws(() => validateFiles(files), /unresolved effect/);
});

test('validates alpha ring geometry and manual-only community package', () => {
    const releases = new URL('../../sample-mods/releases/', import.meta.url);
    const battery = readZip(readFileSync(new URL('Battery-Glow-1.0.doommod', releases)));
    const validated = validateFiles(battery);
    assert.equal(validated.definition.settings.ring.durationSeconds, 3);
    validated.definition.settings.ring.diameterDp = 200;
    battery.set('mod.json', Buffer.from(JSON.stringify(validated.definition)));
    assert.throws(() => validateFiles(battery), /diameterDp/);
    const community = readZip(readFileSync(new URL('Amber-Card.doommod', releases)));
    assert.deepEqual(validateFiles(community).manifest.capabilities, ['ui.preview']);
});
