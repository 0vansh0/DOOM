import capabilityRegistry from '../../../packages/manifest-schema/v2/registry.json';

const actionCapabilities: Record<string, string> = capabilityRegistry.actions;

export type Value = string | number | boolean | { $setting: string };
export type Condition =
  | { op: 'all'; conditions: Condition[] }
  | { op: 'any'; conditions: Condition[] }
  | { op: 'compare'; source: string; comparison: string; value: string | number | boolean };
export type Action = { id: string; effect: string; template?: string; parameters?: Record<string, Value>; independent?: boolean };
export type Trigger = { id: string; event: string; condition: Condition; actions: Action[];
  workflow?: { id: string; executionMode: 'sequential' | 'parallel'; failurePolicy: 'stopOnFailure' | 'continueOnFailure'; timeoutMs: number; maxConcurrency?: number } };
export type Setting = { type: 'boolean'|'integer'|'number'|'duration'|'color'|'enum'|'text'; default: string|number|boolean;
  label: string; description?: string; min?: number; max?: number; step?: number; unit?: 'ms'|'s'|'min'; maxLength?: number; values?: string[] };
export type BuilderProject = { projectFormatVersion: 1; id: string; title: string; manifest: {
  schemaVersion: 3; id: string; name: string; version: string; description: string; author: string; license: string;
  minAndroidApi: number; modType: 'declarative'; entry: 'mod.json'; icon: ''; capabilities: string[]; sdkApiVersion: 2
}; definition: { definitionVersion: 4; triggers: Trigger[]; settings: Record<string, Setting> }; positions: Record<string, { x: number; y: number }> };

export const capabilityNames: Record<string, string> = Object.fromEntries(capabilityRegistry.capabilities
  .filter(capability => capability.implemented).map(capability => [capability.id, capability.displayName]));

const condition = (source: string, comparison: string, value: string|number|boolean): Condition => ({ op: 'compare', source, comparison, value });
const boolSetting: Setting = { type: 'boolean', label: 'Enabled', default: true };

export function createProject(title: string, template = 'blank'): BuilderProject {
  const slug = title.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 38) || 'new-mod';
  const project: BuilderProject = {
    projectFormatVersion: 1, id: crypto.randomUUID(), title,
    manifest: { schemaVersion: 3, id: `local.${slug.replaceAll('-', '')}`, name: title, version: '1.0.0',
      description: 'A declarative DOOM mod built with the Visual Mod Builder.', author: 'Local developer', license: 'MIT',
      minAndroidApi: 31, modType: 'declarative', entry: 'mod.json', icon: '', capabilities: [], sdkApiVersion: 2 },
    definition: { definitionVersion: 4, triggers: [], settings: {} }, positions: {}
  };
  const enabled = { enabled: { ...boolSetting } };
  if (template === 'battery-glow') {
    project.manifest.capabilities = ['battery.read', 'overlay.render'];
    project.definition.settings = { ...enabled,
      accentColor: { type:'color', label:'Accent color', default:'#A78BFA' },
      intensity: { type:'integer', label:'Intensity', min:10, max:60, step:5, default:40 },
      thickness: { type:'integer', label:'Ring thickness', min:2, max:14, step:1, default:7 },
      durationMs: { type:'duration', label:'Effect duration', min:500, max:10000, step:500, unit:'ms', default:3000 }
    };
    project.definition.triggers = [{ id:'charging-started', event:'battery.charging.started', condition: condition('battery.charging','equals',true),
      actions:[{ id:'ring', effect:'overlay.show', template:'battery-ring', parameters:{ color:{ $setting:'accentColor' }, intensity:{ $setting:'intensity' }, ringThicknessDp:{ $setting:'thickness' }, durationMs:{ $setting:'durationMs' } } }] }];
  } else if (template === 'low-battery') {
    project.manifest.capabilities = ['battery.read','ui.preview'];
    project.definition.settings = { ...enabled, durationMs:{ type:'duration',label:'Duration',min:1000,max:10000,step:500,unit:'ms',default:4000 }, message:{ type:'text',label:'Alert message',maxLength:64,default:'Connect your charger' } };
    project.definition.triggers = [{ id:'battery-low', event:'battery.level.changed', condition:{ op:'all', conditions:[condition('battery.level','lessThanOrEqual',20),condition('battery.charging','equals',false),condition('setting.enabled','equals',true)] },
      actions:[{ id:'alert', effect:'ui.preview.show', template:'floating-card', parameters:{ message:{ $setting:'message' }, durationMs:{ $setting:'durationMs' } } }] }];
  } else if (template === 'charging-companion') {
    project.manifest.capabilities = ['battery.read','overlay.render'];
    project.definition.settings = { ...enabled, color:{ type:'color',label:'Ring color',default:'#A78BFA' } };
    project.definition.triggers = [{ id:'charging-workflow', event:'battery.charging.started', condition:condition('battery.charging','equals',true),
      workflow:{ id:'charging-sequence', executionMode:'sequential', failurePolicy:'stopOnFailure', timeoutMs:12000 },
      actions:[{ id:'ring',effect:'overlay.show',template:'battery-ring',parameters:{ color:{ $setting:'color' },durationMs:5000 } },
        { id:'message',effect:'overlay.show',template:'floating-card',parameters:{ message:'Charging started',durationMs:5000 } }] }];
  } else if (template === 'floating-card') {
    project.manifest.capabilities = ['ui.preview'];
    project.definition.settings = { ...enabled, message:{ type:'text',label:'Card text',maxLength:80,default:'Hello from DOOM' } };
    project.definition.triggers = [{ id:'manual-card',event:'mod.manual.trigger',condition:condition('setting.enabled','equals',true),
      actions:[{ id:'card',effect:'ui.preview.show',template:'floating-card',parameters:{ message:{ $setting:'message' },durationMs:3000 } }] }];
  }
  return project;
}

export function compileProject(project: BuilderProject) {
  const manifest = { ...project.manifest, capabilities:[...new Set(project.manifest.capabilities)].sort() };
  const definition = { definitionVersion:4, triggers: project.definition.triggers.map(trigger => ({ ...trigger,
    actions: [...trigger.actions], ...(trigger.workflow ? { workflow:{ ...trigger.workflow } } : {}) })), settings: project.definition.settings };
  return { manifest, definition };
}

export function nodeId(triggerId: string, kind: string, actionId?: string) { return `${triggerId}/${kind}${actionId ? `/${actionId}` : ''}`; }

export function isAllowedConnection(sourceKind: string, targetKind: string, sameTrigger: boolean, sameNode = false) {
  return sameTrigger && !sameNode && ((sourceKind === 'trigger' && targetKind === 'condition') || (sourceKind === 'condition' && targetKind === 'action'));
}

export function validateProjectShape(project: BuilderProject): string[] {
  const errors: string[] = [];
  if (!project.manifest.name.trim()) errors.push('Mod name is required.');
  if (!/^[a-z][a-z0-9.-]{2,63}$/.test(project.manifest.id)) errors.push('Mod ID must use lowercase letters, digits, dots or hyphens.');
  if (!project.definition.triggers.length) errors.push('Add at least one trigger.');
  if (project.definition.triggers.length > 16) errors.push('Definition 4 supports at most 16 triggers.');
  for (const [index, trigger] of project.definition.triggers.entries()) {
    if (trigger.actions.length > 16) errors.push(`Trigger ${trigger.id}: maximum 16 actions.`);
    if (!trigger.actions.length) errors.push(`Trigger ${trigger.id}: add at least one action.`);
    if (trigger.event !== 'mod.manual.trigger' && !project.manifest.capabilities.includes('battery.read')) errors.push(`Trigger ${index + 1}: requires battery.read capability.`);
    for (const action of trigger.actions) {
      const cap = actionCapabilities[action.effect];
      if (!cap) errors.push(`Action ${action.id}: unsupported action ${action.effect}.`);
      else if (!project.manifest.capabilities.includes(cap)) errors.push(`Action ${action.id}: requires ${cap}.`);
      for (const dependency of capabilityRegistry.capabilities.find(item => item.id === cap)?.requiredCapabilities ?? [])
        if (!project.manifest.capabilities.includes(dependency)) errors.push(`Action ${action.id}: also requires ${dependency}.`);
      for (const [key, value] of Object.entries(action.parameters ?? {})) if (typeof value === 'object' && !Object.hasOwn(project.definition.settings, value.$setting)) errors.push(`Action ${action.id}.${key}: unknown setting reference.`);
    }
  }
  return errors;
}
