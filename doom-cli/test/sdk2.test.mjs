import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { validateFiles } from '../dist/format.js';
const cases = JSON.parse(readFileSync(new URL('../../packages/manifest-schema/conformance/sdk2.json', import.meta.url)));
for (const fixture of cases) test('shared SDK conformance: ' + fixture.name, () => {
    const files = new Map([['doom.json', Buffer.from(JSON.stringify(fixture.manifest))],
        ['mod.json', Buffer.from(JSON.stringify(fixture.definition))]]);
    if (fixture.valid) assert.doesNotThrow(() => validateFiles(files));
    else assert.throws(() => validateFiles(files));
});
const v4Cases = JSON.parse(readFileSync(new URL('../../packages/manifest-schema/conformance/v4.json', import.meta.url)));
for (const fixture of v4Cases) test('shared definition v4 conformance: ' + fixture.name, () => {
    const files = new Map([['doom.json', Buffer.from(JSON.stringify(fixture.manifest))],
        ['mod.json', Buffer.from(JSON.stringify(fixture.definition))]]);
    if (fixture.valid) assert.doesNotThrow(() => validateFiles(files));
    else assert.throws(() => validateFiles(files));
});
test('SDK2 templates create independently, package and inspect', () => {
    const root = mkdtempSync(join(tmpdir(), 'doom-sdk2-'));
    const cli = fileURLToPath(new URL('../dist/cli.js', import.meta.url));
    const call = (...args) => spawnSync(process.execPath, [cli, ...args], {cwd:root, encoding:'utf8'});
    try {
        for (const template of ['power-card', 'focus-pulse']) {
            const name = 'custom-' + template;
            for (const args of [['create', name, '--template', template], ['validate', name], ['package', name], ['inspect', name + '.doommod']]) {
                const result = call(...args);
                assert.equal(result.status, 0, result.stderr);
            }
        }
        const metadata = JSON.parse(call('capabilities').stdout);
        assert.equal(metadata.sdkApiVersion, 2);
        assert.equal(metadata.registryVersion, 2);
        assert.equal(metadata.capabilities.length, 11);
        assert.ok(metadata.capabilities.every(cap => cap.contractVersion === 1 && cap.doomApproval && cap.tier === 'STANDARD'));
        assert.equal(metadata.capabilities.find(cap => cap.id === 'app.launch.approved').implemented, false);
    } finally { rmSync(root, {recursive:true, force:true}); }
});

test('overlay preset uses the capability catalog dependencies', () => {
    const fixture = v4Cases.find(item => item.name === 'v4-valid-composed-multi-action');
    const manifest = structuredClone(fixture.manifest);
    const definition = structuredClone(fixture.definition);
    manifest.capabilities.push('overlay.render', 'overlay.preset.apply');
    const action = definition.triggers[0].actions[0];
    action.effect = 'overlay.preset.apply';
    action.template = 'battery-ring';
    const valid = new Map([['doom.json', Buffer.from(JSON.stringify(manifest))], ['mod.json', Buffer.from(JSON.stringify(definition))]]);
    assert.doesNotThrow(() => validateFiles(valid));
    manifest.capabilities = manifest.capabilities.filter(capability => capability !== 'overlay.render');
    const denied = new Map([['doom.json', Buffer.from(JSON.stringify(manifest))], ['mod.json', Buffer.from(JSON.stringify(definition))]]);
    assert.throws(() => validateFiles(denied), /also requires capability overlay\.render/);
});

test('display metadata action is parameterless and uses the registry capability', () => {
    const fixture = v4Cases.find(item => item.name === 'v4-valid-composed-multi-action');
    const manifest = structuredClone(fixture.manifest);
    const definition = structuredClone(fixture.definition);
    manifest.capabilities.push('device.display.read');
    const action = definition.triggers[0].actions[0];
    action.effect = 'device.display.read';
    delete action.template;
    delete action.parameters;
    const valid = new Map([['doom.json', Buffer.from(JSON.stringify(manifest))], ['mod.json', Buffer.from(JSON.stringify(definition))]]);
    assert.doesNotThrow(() => validateFiles(valid));
    action.parameters = { target: 'private-id' };
    const invalid = new Map([['doom.json', Buffer.from(JSON.stringify(manifest))], ['mod.json', Buffer.from(JSON.stringify(definition))]]);
    assert.throws(() => validateFiles(invalid), /Display metadata action accepts no parameters/);
});


test('privileged setting operations are catalogued but unavailable to mod definitions', () => {
    const registry = JSON.parse(readFileSync(new URL('../../packages/manifest-schema/v2/registry.json', import.meta.url)));
    assert.deepEqual(registry.privilegedOperations.map(operation => operation.id), [
        'system.screen_timeout.read', 'system.screen_timeout.set', 'system.screen_timeout.restore'
    ]);
    assert.ok(registry.privilegedOperations.every(operation => operation.modAccessible === false));
    assert.ok(registry.privilegedOperations.every(operation => !Object.hasOwn(registry.actions, operation.id)));
});
