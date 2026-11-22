import { readFileSync } from 'node:fs';
import { Ajv2020 } from 'ajv/dist/2020.js';
import { crc32, inflateSync } from 'node:zlib';

export const compressedLimit = 10 * 1024 * 1024;
export const expandedLimit = 25 * 1024 * 1024;
export const assetPath = /^assets\/[a-z0-9][a-z0-9_-]{0,39}\.png(?![\s\S])/;
export const schemaRoot = new URL('./schema/', import.meta.url);
type CapabilityCatalog = { registryVersion: number; capabilities: { id: string; requiredCapabilities?: string[]; implemented: boolean }[]; actions: Record<string, string> };
const capabilityCatalog = JSON.parse(readFileSync(new URL('v2/registry.json', schemaRoot), 'utf8')) as CapabilityCatalog;
const capabilityDefinitions = new Map(capabilityCatalog.capabilities.map(entry => [entry.id, entry]));

export function check(condition: unknown, message: string): asserts condition {
    if (!condition) throw new Error(message);
}

export function entryLimit(name: string): number {
    if (name === 'doom.json') return 64 * 1024;
    if (name === 'mod.json') return 256 * 1024;
    check(assetPath.test(name), 'Unsupported archive entry path.');
    return 5 * 1024 * 1024;
}

const ajv = new Ajv2020();
const schema = (name: string) => ajv.compile(JSON.parse(readFileSync(new URL('v2/' + name + '.schema.json', schemaRoot), 'utf8')));
const manifests = new Map([[2, schema('doom')], [3, schema('doom-sdk2')]]);
const definitions = new Map([[1, schema('mod')], [2, schema('mod-v2')], [3, schema('mod-sdk2')], [4, schema('mod-v4')]]);

export function validateFiles(files: Map<string, Buffer>) {
    check(capabilityCatalog.registryVersion === 2, 'Unsupported capability registry version.');
    check(files.size >= 2 && files.size <= 50, 'Expected 2–50 package entries.');
    let total = 0;
    for (const [name, bytes] of files) {
        check(bytes.length > 0 && bytes.length <= entryLimit(name), 'Invalid entry size.');
        total += bytes.length;
        check(total <= expandedLimit, 'Archive exceeds 25 MiB expanded.');
        if (assetPath.test(name)) validatePng(bytes);
    }
    const m = files.get('doom.json');
    const d = files.get('mod.json');
    check(m, 'Missing doom.json.');
    check(d, 'Missing mod.json.');
    const manifest = strictJson(m);
    const definition = strictJson(d);
    const manifestVersion = (manifest as { schemaVersion?: number } | null)?.schemaVersion;
    const checkManifest = manifests.get(manifestVersion ?? 0);
    check(checkManifest, 'Unsupported manifest version.');
    check(checkManifest(manifest), 'Invalid doom.json: ' + ajv.errorsText(checkManifest.errors));
    const version = (definition as { definitionVersion?: number } | null)?.definitionVersion;
    check(manifestVersion === 3 ? version === 3 || version === 4 : version === 1 || version === 2, 'Manifest and SDK definition versions do not match.');
    const checkDefinition = definitions.get(version ?? 0);
    check(checkDefinition, 'Unsupported definition version.');
    check(checkDefinition(definition), 'Invalid mod.json: ' + ajv.errorsText(checkDefinition.errors));
    const validManifest = manifest as { id: string; name: string; capabilities: string[]; icon: string };
    const validDefinition = definition as { settings: { intensity: { min: number; max: number; default: number } } };
    if (version === 4) {
        validateV4Bindings(definition as V4Definition, validManifest.capabilities);
        check(validManifest.icon === '' || files.has(validManifest.icon), 'The declared icon is missing.');
        return { manifest: validManifest, definition };
    }
    const settings = definition as Definition;
    validateBindings(settings, validManifest.capabilities);
    check(!settings.settings.ring || settings.effects.some(effect => effect.template === 'battery-ring'),
        'Ring settings require the battery-ring template.');
    check(validManifest.icon === '' || files.has(validManifest.icon), 'The declared icon is missing.');
    const { min, max, default: level } = validDefinition.settings.intensity;
    check(level >= min && level <= max, 'Intensity default is outside its declared range.');
    return { manifest: validManifest, definition };
}

type V4Condition = { op: 'all'; conditions: V4Condition[] } | { op: 'any'; conditions: V4Condition[] } |
    { op: 'compare'; source: string; comparison: string; value: number | boolean | string };
type V4Definition = { definitionVersion: 4; triggers: { id: string; event: string; condition: V4Condition;
    actions: { id?: string; effect: string; template?: string; independent?: boolean;
        parameters?: Record<string, string | number | boolean | { $setting: string }> }[];
    workflow?: { id: string; executionMode: 'sequential'|'parallel'; failurePolicy: 'stopOnFailure'|'continueOnFailure'; timeoutMs: number; maxConcurrency?: number } }[];
    settings: Record<string, { type: string; default: string | number | boolean; label?: string; description?: string;
        min?: number; max?: number; step?: number; unit?: 'ms'|'s'|'min'; maxLength?: number; values?: string[] }> };

function validateV4Bindings(definition: V4Definition, capabilities: string[]) {
    const ids = new Set<string>();
    const workflowIds = new Set<string>();
    let nodes = 0;
    let leaves = 0;
    function walk(condition: V4Condition, depth: number) {
        check(depth <= 4 && ++nodes <= 64, 'Condition complexity exceeds the supported limit.');
        if (condition.op === 'all' || condition.op === 'any') return condition.conditions.forEach(child => walk(child, depth + 1));
        check(++leaves <= 32, 'Condition leaf count exceeds the supported limit.');
        check(['below', 'above', 'equals', 'lessThan', 'lessThanOrEqual', 'greaterThan', 'greaterThanOrEqual', 'notEquals'].includes(condition.comparison), 'Unknown condition operator.');
        if (condition.source.startsWith('battery.')) check(capabilities.includes('battery.read'), 'Condition requires battery.read.');
        if (condition.source === 'battery.level') check(typeof condition.value === 'number' && Number.isInteger(condition.value) && condition.value >= 0 && condition.value <= 100, 'Battery condition expects integer 0..100.');
        else if (condition.source === 'battery.charging') check(['equals', 'notEquals'].includes(condition.comparison) && typeof condition.value === 'boolean', 'Charging condition expects boolean equality.');
        else if (condition.source.startsWith('setting.')) {
            const id = condition.source.slice('setting.'.length);
            const setting = definition.settings[id];
            check(setting, `Unknown setting condition reference: ${id}.`);
            if (['integer', 'number', 'duration'].includes(setting.type)) check(typeof condition.value === 'number', `Setting ${id} requires a numeric condition value.`);
            else if (setting.type === 'boolean') check(['equals', 'notEquals'].includes(condition.comparison) && typeof condition.value === 'boolean', `Setting ${id} requires boolean equality.`);
            else check(['equals', 'notEquals'].includes(condition.comparison) && typeof condition.value === 'string', `Setting ${id} requires string equality.`);
        } else check(condition.source === 'event.name' && ['equals', 'notEquals'].includes(condition.comparison) && typeof condition.value === 'string', 'Event metadata supports string equality.');
    }
    for (const trigger of definition.triggers) {
        check(!ids.has(trigger.id), 'Trigger IDs must be unique.'); ids.add(trigger.id);
        if (trigger.workflow) {
            check(!workflowIds.has(trigger.workflow.id), 'Workflow IDs must be unique.'); workflowIds.add(trigger.workflow.id);
            const actionIds = trigger.actions.map(action => action.id ?? '');
            check(actionIds.every(id => /^[a-z][a-z0-9-]{0,31}$/.test(id)) && new Set(actionIds).size === actionIds.length,
                'Workflow actions must have unique stable IDs.');
            if (trigger.workflow.executionMode === 'parallel' || trigger.workflow.failurePolicy === 'continueOnFailure')
                check(trigger.actions.slice(1).every(action => action.independent === true), 'Parallel/continue workflows require later actions to be marked independent.');
        }
        if (trigger.event !== 'mod.manual.trigger') check(capabilities.includes('battery.read'), `Trigger ${trigger.event} requires battery.read.`);
        walk(trigger.condition, 1);
        for (const action of trigger.actions) {
            const required = capabilityCatalog.actions[action.effect];
            check(required && capabilities.includes(required), `Unknown action or missing capability: ${action.effect}.`);
            for (const dependency of capabilityDefinitions.get(required)?.requiredCapabilities ?? [])
                check(capabilities.includes(dependency), `Action ${action.effect} also requires capability ${dependency}.`);
            check(['overlay.dismiss', 'haptics.pulse', 'device.display.read'].includes(action.effect) ? !action.template : !!action.template,
                `Invalid template for action ${action.effect}.`);
            if (action.effect === 'device.display.read') check(Object.keys(action.parameters ?? {}).length === 0,
                'Display metadata action accepts no parameters or template.');
            for (const [key, value] of Object.entries(action.parameters ?? {})) check(/^[a-z][a-zA-Z0-9]{0,31}$/.test(key) &&
                (typeof value === 'boolean' || typeof value === 'number' && Number.isFinite(value) || typeof value === 'string' && value.length <= 128 ||
                    typeof value === 'object' && value !== null && typeof value.$setting === 'string' && !!definition.settings[value.$setting]), 'Invalid action parameter or unresolved setting reference.');
        }
    }
    check(Object.keys(definition.settings).length <= 24, 'A mod may declare at most 24 settings.');
    for (const [id, setting] of Object.entries(definition.settings)) {
        check(/^[a-z][a-zA-Z0-9]{0,31}$/.test(id), 'Invalid setting ID.');
        check(setting.label === undefined || setting.label.length >= 1 && setting.label.length <= 48, `Invalid setting label ${id}.`);
        check(!setting.description || setting.description.length <= 120, `Invalid setting description ${id}.`);
        const typeFields: Record<string, string[]> = {
            boolean: ['type','default','label','description'], integer: ['type','default','label','description','min','max','step'],
            number: ['type','default','label','description','min','max','step'], duration: ['type','default','label','description','min','max','step','unit'],
            color: ['type','default','label','description'], enum: ['type','default','label','description','values'],
            text: ['type','default','label','description','maxLength']
        };
        check(Object.keys(setting).every(key => typeFields[setting.type]?.includes(key)), `Unsupported field for ${setting.type} setting ${id}.`);
        if (setting.type === 'boolean') check(typeof setting.default === 'boolean', `Invalid boolean setting ${id}.`);
        else if (setting.type === 'integer' || setting.type === 'number' || setting.type === 'duration') {
            check(typeof setting.default === 'number' && Number.isFinite(setting.default) && Number.isFinite(setting.min) && Number.isFinite(setting.max), `Invalid numeric setting ${id}.`);
            const step = setting.step ?? (setting.type === 'integer' ? 1 : Math.max((setting.max! - setting.min!) / 100, 0.000001));
            check(setting.min! < setting.max! && step > 0 && setting.max! - setting.min! <= 1_000_000, `Invalid numeric range ${id}.`);
            if (setting.type === 'integer') check(Number.isInteger(setting.min) && Number.isInteger(setting.max) && Number.isInteger(step) && Number.isInteger(setting.default), `Integer setting ${id} needs integer bounds and step.`);
            if (setting.type === 'duration') check(['ms', 's', 'min'].includes(setting.unit ?? ''), `Duration setting ${id} needs a supported unit.`);
            else check(setting.unit === undefined, `Only duration settings may declare a unit: ${id}.`);
            const intervals = (setting.default - setting.min!) / step;
            check(setting.min! <= setting.default && setting.default <= setting.max! && Math.abs(intervals - Math.round(intervals)) < 1e-8, `Invalid setting default or step ${id}.`);
        }
        else if (setting.type === 'color') check(typeof setting.default === 'string' && /^#[0-9a-fA-F]{6}$/.test(setting.default), `Invalid color setting ${id}.`);
        if (setting.type === 'enum') check(setting.values?.includes(String(setting.default)), `Invalid enum default ${id}.`);
        if (setting.type === 'enum') check(setting.values!.length > 0 && setting.values!.length <= 16 && new Set(setting.values).size === setting.values!.length, `Invalid enum choices ${id}.`);
        if (setting.type === 'text') check(typeof setting.default === 'string' && setting.maxLength! >= 1 && setting.maxLength! <= 256 && setting.default.length <= setting.maxLength!, `Invalid text setting ${id}.`);
        check(['boolean','integer','number','duration','color','enum','text'].includes(setting.type), `Unsupported setting type ${setting.type}.`);
    }
}

type Definition = {
    settings: { ring?: object; options?: { id: string; type: string; min: number; max: number; default: number }[] };
    effects: { id: string; effect: string; template?: string; params?: object }[];
    triggers: { effect: string; event: string; condition?: string; setting?: string }[];
};

function validateBindings(definition: Definition, capabilities: string[]) {
    const options = definition.settings.options ?? [];
    check(new Set(options.map(item => item.id)).size === options.length, 'Duplicate setting IDs.');
    check(options.every(item => item.min <= item.default && item.default <= item.max), 'Invalid setting default or range.');
    const effects = new Map(definition.effects.map(effect => [effect.id, effect]));
    check(effects.size === definition.effects.length, 'Effect IDs must be unique.');
    for (const effect of effects.values()) {
        const required = capabilityCatalog.actions[effect.effect];
        check(!!required && capabilities.includes(required), 'Effect references an unknown action or undeclared capability.');
        for (const dependency of capabilityDefinitions.get(required)?.requiredCapabilities ?? [])
            check(capabilities.includes(dependency), `Effect also requires capability ${dependency}.`);
        check(['overlay.dismiss', 'haptics.pulse'].includes(effect.effect) ? !effect.template && !effect.params : effect.template && effect.params,
            'Invalid effect parameters.');
    }
    const events = new Set<string>();
    const used = new Set<string>();
    for (const trigger of definition.triggers) {
        check(!events.has(trigger.event) && effects.has(trigger.effect), 'Duplicate event or unresolved effect.');
        const eventCapability = trigger.event === 'device.power.changed' ? 'device.power.read'
            : trigger.event === 'mod.manual.trigger' ? undefined : 'battery.read';
        const conditionCapability = ['power-saving', 'not-power-saving'].includes(trigger.condition ?? '')
            ? 'device.power.read' : !trigger.condition || trigger.condition === 'always' ? undefined : 'battery.read';
        check([eventCapability, conditionCapability].every(cap => !cap || capabilities.includes(cap)),
            'Trigger references an undeclared capability.');
        check(trigger.condition === 'battery-below-setting' ? options.some(item => item.id === trigger.setting)
            : trigger.setting === undefined, 'Invalid condition setting reference.');
        events.add(trigger.event);
        used.add(trigger.effect);
    }
    check(used.size === effects.size, 'All effects must be referenced.');
}

function strictJson(bytes: Buffer): unknown {
    const text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
    let position = 0;
    const ws = /[ \t\n\r]*/y;
    const string = /"(?:[^"\\\u0000-\u001f]|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*"/y;
    const number = /-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/y;
    function token(pattern: RegExp): string | undefined {
        pattern.lastIndex = position;
        const match = pattern.exec(text);
        if (match) position = pattern.lastIndex;
        return match?.[0];
    }
    function take(character: string): boolean {
        token(ws);
        if (text[position] !== character) return false;
        position++;
        return true;
    }
    function readString(): string {
        token(ws);
        const quoted = token(string);
        check(quoted, 'Invalid JSON string.');
        const value = JSON.parse(quoted) as string;
        check(Buffer.from(value).toString('utf8') === value, 'Invalid Unicode in JSON.');
        return value;
    }
    function read(depth: number): unknown {
        check(depth <= 12, 'JSON is nested too deeply.');
        if (take('{')) {
            const fields: Record<string, unknown> = Object.create(null);
            if (!take('}')) {
                do {
                    const name = readString();
                    check(!Object.hasOwn(fields, name) && take(':'), 'Duplicate property or invalid JSON object.');
                    fields[name] = read(depth + 1);
                } while (take(','));
                check(take('}'), 'Invalid JSON object.');
            }
            return fields;
        }
        if (take('[')) {
            const entries: unknown[] = [];
            if (!take(']')) {
                do { entries.push(read(depth + 1)); } while (take(','));
                check(take(']'), 'Invalid JSON array.');
            }
            return entries;
        }
        if (text[position] === '"') return readString();
        if (text.startsWith('true', position)) { position += 4; return true; }
        if (text.startsWith('false', position)) { position += 5; return false; }
        const digits = token(number);
        const value = Number(digits);
        check(digits !== undefined && Number.isFinite(value), 'Expected a finite JSON number.');
        return value;
    }
    const value = read(0);
    token(ws);
    check(position === text.length, 'Trailing JSON data.');
    return value;
}

function validatePng(bytes: Buffer) {
    check(bytes.length >= 45 && bytes.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex')), 'Expected a PNG asset.');
    let position = 8, stride = 0, height = 0, end = false;
    const parts: Buffer[] = [];
    while (position <= bytes.length - 12 && !end) {
        const length = bytes.readUInt32BE(position);
        check(length <= bytes.length - position - 12, 'Invalid PNG chunk size.');
        const type = bytes.toString('latin1', position + 4, position + 8);
        const data = bytes.subarray(position + 8, position + 8 + length);
        check(crc32(bytes.subarray(position + 4, position + 8 + length)) === bytes.readUInt32BE(position + 8 + length), 'Invalid PNG checksum.');
        switch (type) {
            case 'IHDR': {
                check(position === 8 && length === 13, 'Invalid PNG header.');
                const width = data.readUInt32BE(0);
                height = data.readUInt32BE(4);
                check(width >= 1 && width <= 2048 && height >= 1 && height <= 2048 && width * height <= 1_048_576 &&
                    data[8] === 8 && [2, 6].includes(data[9]) && data.subarray(10).every(value => value === 0), 'Unsupported PNG format or dimensions.');
                stride = 1 + width * (data[9] === 2 ? 3 : 4);
                break;
            }
            case 'IDAT':
                check(stride > 0 && length > 0, 'Invalid PNG image data.');
                parts.push(data);
                break;
            case 'IEND':
                check(length === 0 && parts.length > 0 && position + 12 === bytes.length, 'Invalid PNG end.');
                end = true;
                break;
            default: throw new Error('Unsupported PNG chunk.');
        }
        position += 12 + length;
    }
    check(end, 'Incomplete PNG asset.');
    const compressed = Buffer.concat(parts);
    const decoded = inflateSync(compressed, { maxOutputLength: height * stride + 1, info: true });
    // The convenience API returns the engine when info is enabled.
    const result = decoded as unknown as { buffer: Buffer; engine: { bytesWritten: number } };
    check(result.buffer.length === height * stride && result.engine.bytesWritten === compressed.length, 'Invalid PNG expansion.');
    for (let row = 0; row < height; row++) check(result.buffer[row * stride] <= 4, 'Invalid PNG filter.');
}
