import { describe, expect, it } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { capabilityNames, compileProject, createProject, isAllowedConnection, validateProjectShape } from './project';
import { pushHistory, redoHistory, undoHistory, type History } from './history';

const cli=resolve(process.cwd(),'../doom-cli/dist/cli.js');

describe('visual project graph and history',()=>{
  it('allows only forward trigger-condition-action edges within one trigger',()=>{
    expect(isAllowedConnection('trigger','condition',true)).toBe(true);
    expect(isAllowedConnection('condition','action',true)).toBe(true);
    expect(isAllowedConnection('action','trigger',true)).toBe(false);
    expect(isAllowedConnection('trigger','action',true)).toBe(false);
    expect(isAllowedConnection('trigger','condition',false)).toBe(false);
    expect(isAllowedConnection('condition','condition',true)).toBe(false);
  });
  it('undoes and redoes an immutable project edit',()=>{
    const original={name:'one'};const changed={name:'two'};
    let history:History<typeof original>={past:[],future:[]};history=pushHistory(history,original);
    const undone=undoHistory(history,changed);expect(undone?.current).toEqual(original);
    const redone=redoHistory(undone!.history,undone!.current);expect(redone?.current).toEqual(changed);
  });
  it('keeps action array order in the canonical compiler',()=>{
    const project=createProject('Companion','charging-companion');
    expect(compileProject(project).definition.triggers[0].actions.map(a=>a.id)).toEqual(['ring','message']);
  });
});

describe('public SDK compilation and CLI compatibility',()=>{
  const templates=['battery-glow','low-battery','charging-companion','floating-card'] as const;
  it.each(templates)('%s compiles as valid Manifest 3 / Definition 4 and packages via doom-cli',template=>{
    const project=createProject(`test-${template}`,template);project.manifest.id=`local.test${template.replaceAll('-','')}`;
    expect(validateProjectShape(project)).toEqual([]);
    const compiled=compileProject(project);expect(compiled.manifest.sdkApiVersion).toBe(2);expect(compiled.definition.definitionVersion).toBe(4);
    const temp=mkdtempSync(join(tmpdir(),'doom-builder-test-'));
    try{
      writeFileSync(join(temp,'doom.json'),JSON.stringify(compiled.manifest));writeFileSync(join(temp,'mod.json'),JSON.stringify(compiled.definition));
      expect(execFileSync(process.execPath,[cli,'validate',temp],{encoding:'utf8'})).toContain(compiled.manifest.id);
      execFileSync(process.execPath,[cli,'package',temp],{stdio:'pipe'});
      const archive=`${temp}.doommod`;
      expect(execFileSync(process.execPath,[cli,'validate',archive],{encoding:'utf8'})).toContain(compiled.manifest.id);
      rmSync(archive,{force:true});
    }finally{rmSync(temp,{recursive:true,force:true});}
  });
  it('uses Registry v2 for display and overlay preset authoring',()=>{
    expect(capabilityNames['device.display.read']).toBeTruthy();
    expect(capabilityNames['overlay.preset.apply']).toBeTruthy();
    expect(capabilityNames['app.launch.approved']).toBeUndefined();
    const project=createProject('Preset');
    project.manifest.capabilities=['overlay.render','overlay.preset.apply'];
    project.definition.triggers=[{id:'manual',event:'mod.manual.trigger',condition:{op:'compare',source:'event.name',comparison:'equals',value:'mod.manual.trigger'},
      actions:[{id:'preset',effect:'overlay.preset.apply',template:'battery-ring'}]}];
    expect(validateProjectShape(project)).toEqual([]);
    const temp=mkdtempSync(join(tmpdir(),'doom-preset-test-'));
    try{
      const compiled=compileProject(project);
      writeFileSync(join(temp,'doom.json'),JSON.stringify(compiled.manifest));writeFileSync(join(temp,'mod.json'),JSON.stringify(compiled.definition));
      expect(execFileSync(process.execPath,[cli,'validate',temp],{encoding:'utf8'})).toContain(compiled.manifest.id);
      project.manifest.capabilities=['overlay.preset.apply'];
      expect(validateProjectShape(project).some(message=>message.includes('also requires overlay.render'))).toBe(true);
    }finally{rmSync(temp,{recursive:true,force:true});}
  });
  it('does not expose app-only privileged setting operations as mod actions',()=>{
    const registry=JSON.parse(readFileSync(resolve(process.cwd(),'../packages/manifest-schema/v2/registry.json'),'utf8'));
    expect(registry.privilegedOperations.every((operation:{modAccessible:boolean})=>operation.modAccessible===false)).toBe(true);
    expect(Object.keys(registry.actions)).not.toContain('system.screen_timeout.set');
    expect(capabilityNames['device.settings.screen_timeout.write']).toBeUndefined();
  });
  it('round-trips the versioned local builder project format',()=>{
    const project=createProject('Round Trip','battery-glow');
    const copy=JSON.parse(JSON.stringify(project));
    expect(copy.projectFormatVersion).toBe(1);
    expect(copy.definition.definitionVersion).toBe(4);
    expect(copy.definition.settings).toEqual(project.definition.settings);
  });
  it('rejects a missing capability before export',()=>{
    const project=createProject('No Battery','battery-glow');project.manifest.capabilities=[];
    expect(validateProjectShape(project)).toContain('Trigger 1: requires battery.read capability.');
  });
});
