import { readFileSync, lstatSync } from 'node:fs';
import { resolve } from 'node:path';
import { check, validateFiles } from './format.js';
import { readZip } from './zip.js';

type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
type EventInput = { event: string; metadata?: Record<string, Json> };
type Condition = { op: 'all'; conditions: Condition[] } | { op: 'any'; conditions: Condition[] } |
    { op: 'compare'; source: string; comparison: string; value: Json };

function loadJson(path: string): Json {
    const stat = lstatSync(path);
    check(stat.isFile() && stat.size > 0 && stat.size <= 1024 * 1024, 'Scenario must be a regular JSON file no larger than 1 MiB.');
    return JSON.parse(readFileSync(path, 'utf8')) as Json;
}

function compare(left: Json | undefined, op: string, right: Json): boolean {
    if (left === undefined || typeof left !== typeof right || (typeof left === 'object' && left !== null)) return false;
    switch (op) {
        case 'equals': return left === right;
        case 'notEquals': return left !== right;
        case 'below': case 'lessThan': return typeof left === 'number' && typeof right === 'number' && left < right;
        case 'above': case 'greaterThan': return typeof left === 'number' && typeof right === 'number' && left > right;
        case 'lessThanOrEqual': return typeof left === 'number' && typeof right === 'number' && left <= right;
        case 'greaterThanOrEqual': return typeof left === 'number' && typeof right === 'number' && left >= right;
        default: throw new Error(`Unsupported condition operator: ${op}`);
    }
}

function settingValue(settingsSchema: Record<string, any>, provided: Record<string, Json>, key: string): Json | undefined {
    const spec = settingsSchema?.[key];
    if (!spec) return undefined;
    const value = Object.hasOwn(provided, key) ? provided[key] : spec.default as Json;
    if (typeof value !== typeof spec.default) return undefined;
    if (typeof value === 'number' && (value < spec.min || value > spec.max)) return undefined;
    if (spec.type === 'enum' && !spec.values.includes(value)) return undefined;
    if (spec.type === 'color' && (typeof value !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(value))) return undefined;
    if (spec.type === 'text' && (typeof value !== 'string' || value.length > spec.maxLength)) return undefined;
    return value;
}

function evaluate(node: Condition, metadata: Record<string, Json>, settings: Record<string, Json>, depth = 1, meter = { nodes: 0 }): boolean {
    check(depth <= 4 && ++meter.nodes <= 64, 'Condition complexity exceeds simulator limits (depth 4, nodes 64).');
    if (node.op === 'all') return node.conditions.every(child => evaluate(child, metadata, settings, depth + 1, meter));
    if (node.op === 'any') return node.conditions.some(child => evaluate(child, metadata, settings, depth + 1, meter));
    let value: Json | undefined;
    if (node.source.startsWith('setting.')) value = settings[node.source.slice(8)];
    else value = metadata[node.source];
    const expected = node.value && typeof node.value === 'object' && !Array.isArray(node.value) && '$setting' in node.value
        ? settings[String(node.value.$setting)] : node.value;
    return compare(value, node.comparison, expected);
}

function eventValue(event: EventInput): Record<string, Json> {
    const metadata = { ...(event.metadata ?? {}) };
    metadata['event.name'] = event.event;
    if (typeof metadata.batteryLevel === 'number') metadata['battery.level'] = metadata.batteryLevel;
    if (typeof metadata.charging === 'boolean') metadata['battery.charging'] = metadata.charging;
    return metadata;
}

export function runScenarios(projectDir: string, scenarioPath: string): { passed: number; failed: number } {
    const entries = new Map<string, Buffer>();
    const directory = resolve(projectDir);
    for (const name of ['doom.json', 'mod.json']) entries.set(name, readFileSync(resolve(directory, name)));
    const { manifest, definition } = validateFiles(entries) as any;
    let settingsSchema: Record<string, any> = definition.settings ?? {};
    let triggers: any[] = definition.triggers ?? [];
    if (definition.definitionVersion === 3) {
        const legacySettings = settingsSchema;
        settingsSchema = Object.fromEntries((legacySettings.options ?? []).map((item: any) => [item.id, item]));
        const effects = new Map((definition.effects ?? []).map((effect: any) => [effect.id, effect]));
        triggers = definition.triggers.map((trigger: any) => {
            let condition: Condition;
            switch (trigger.condition ?? 'always') {
                case 'always': condition = { op: 'compare', source: 'event.name', comparison: 'equals', value: trigger.event }; break;
                case 'charging': condition = { op: 'compare', source: 'battery.charging', comparison: 'equals', value: true }; break;
                case 'not-charging': condition = { op: 'compare', source: 'battery.charging', comparison: 'equals', value: false }; break;
                case 'battery-low': condition = { op: 'compare', source: 'battery.level', comparison: 'lessThanOrEqual', value: 20 }; break;
                case 'battery-not-low': condition = { op: 'compare', source: 'battery.level', comparison: 'greaterThan', value: 20 }; break;
                case 'battery-below-setting': condition = { op: 'compare', source: 'battery.level', comparison: 'lessThan', value: { $setting: trigger.setting } as unknown as Json }; break;
                case 'power-saving': condition = { op: 'compare', source: 'device.power.saving', comparison: 'equals', value: true }; break;
                case 'not-power-saving': condition = { op: 'compare', source: 'device.power.saving', comparison: 'equals', value: false }; break;
                default: throw new Error(`Unsupported legacy condition: ${trigger.condition}`);
            }
            const effect: any = effects.get(trigger.effect);
            check(effect, `Legacy trigger references unknown effect ${trigger.effect}.`);
            return { id: trigger.event, event: trigger.event, condition, actions: [{ id: effect.id, effect: effect.effect,
                template: effect.template, parameters: effect.params }] };
        });
    }
    const suite = loadJson(resolve(scenarioPath)) as any;
    check(suite && suite.formatVersion === 1 && Array.isArray(suite.scenarios) && suite.scenarios.length > 0 && suite.scenarios.length <= 100,
        'Scenario file must contain formatVersion 1 and 1–100 scenarios.');
    let passed = 0, failed = 0;
    for (const scenario of suite.scenarios) {
        const id = String(scenario.id ?? '<unnamed>');
        try {
            check(typeof scenario.event?.event === 'string' && scenario.expected && typeof scenario.expected === 'object', `${id}: invalid scenario structure.`);
            const settings: Record<string, Json> = Object.create(null);
            for (const key of Object.keys(settingsSchema)) {
                const value = settingValue(settingsSchema, scenario.initialSettings ?? {}, key);
                check(value !== undefined, `${id}: invalid setting ${key}.`); settings[key] = value;
            }
            check(Object.keys(scenario.initialSettings ?? {}).every(key => Object.hasOwn(settings, key)), `${id}: unknown initial setting.`);
            const timeline: EventInput[] = [...(scenario.precedingEvents ?? []), scenario.event];
            const actionSequence: string[] = [], suppressedActions: string[] = [];
            const resolvedParameters: Record<string, Record<string, Json>> = Object.create(null);
            let conditionResult = false, failure: string | undefined;
            for (const input of timeline) {
                check(typeof input.event === 'string' && input.metadata === undefined || typeof input.metadata === 'object', `${id}: malformed event.`);
                for (const trigger of triggers) {
                    if (trigger.event !== input.event) continue;
                    const metadata = eventValue(input);
                    conditionResult = evaluate(trigger.condition, metadata, settings);
                    const actions = trigger.actions ?? (trigger.effect ? [{ id: trigger.effect, effect: trigger.effect }] : []);
                    for (const action of actions) {
                        const actionId = `${trigger.id}:${action.id ?? action.effect}`;
                        const needed = ({ 'overlay.show': 'overlay.render', 'overlay.dismiss': 'overlay.render', 'ui.preview.show': 'ui.preview', 'haptics.pulse': 'haptics.vibrate' } as Record<string, string>)[action.effect];
                        const params = Object.fromEntries(Object.entries(action.parameters ?? {}).map(([key, value]: [string, any]) =>
                            [key, value && typeof value === 'object' && '$setting' in value ? settings[value.$setting] : value]));
                        const denied = !conditionResult || !manifest.capabilities.includes(needed) ||
                            !(scenario.grantedCapabilities ?? manifest.capabilities).includes(needed) ||
                            (needed === 'overlay.render' && !(scenario.androidPermissions ?? []).includes('SYSTEM_ALERT_WINDOW'));
                        if (denied) { suppressedActions.push(actionId); continue; }
                        if ((scenario.failActions ?? []).includes(actionId)) {
                            failure = actionId; suppressedActions.push(actionId);
                            if ((trigger.workflow?.failurePolicy ?? 'stopOnFailure') === 'stopOnFailure') break;
                            continue;
                        }
                        // This records a deterministic simulated action only; no Android API is invoked.
                        actionSequence.push(actionId);
                        resolvedParameters[actionId] = params as Record<string, Json>;
                    }
                }
            }
            const expected = scenario.expected;
            check(expected.conditionResult === undefined || expected.conditionResult === conditionResult, `${id}: condition result mismatch.`);
            check(JSON.stringify(expected.actionSequence ?? []) === JSON.stringify(actionSequence), `${id}: action sequence mismatch; got ${JSON.stringify(actionSequence)}.`);
            check(JSON.stringify(expected.suppressedActions ?? []) === JSON.stringify(suppressedActions), `${id}: suppressed action mismatch; got ${JSON.stringify(suppressedActions)}.`);
            check(expected.failureAction === undefined || expected.failureAction === failure, `${id}: failure result mismatch.`);
            check(expected.resolvedParameters === undefined || JSON.stringify(expected.resolvedParameters) === JSON.stringify(resolvedParameters),
                `${id}: resolved action parameters mismatch.`);
            console.log(`PASS ${id}`); passed++;
        } catch (error) {
            console.error(`FAIL ${id}: ${error instanceof Error ? error.message : error}`); failed++;
        }
    }
    console.log(`Scenarios: ${passed} passed, ${failed} failed (desktop simulation; Android effects and hardware are not executed).`);
    return { passed, failed };
}
