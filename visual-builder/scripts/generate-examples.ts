import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { compileProject, createProject, validateProjectShape } from '../src/lib/project';

const root = resolve(import.meta.dirname, '../..');
const cli = resolve(root, 'doom-cli/dist/cli.js');
const examples = resolve(import.meta.dirname, '../examples');
const exportsDir = resolve(import.meta.dirname, '../exports');
const kotlinFixtures = resolve(import.meta.dirname, '../kotlin-fixtures');
mkdirSync(examples, { recursive:true }); mkdirSync(exportsDir, { recursive:true }); mkdirSync(kotlinFixtures,{recursive:true});

const definitions = [
  { template:'battery-glow', slug:'battery-glow', title:'Battery Glow Customization', scenario:{event:'battery.charging.started',metadata:{batteryLevel:72,charging:true}} },
  { template:'low-battery', slug:'low-battery-alert', title:'Low Battery Alert', scenario:{event:'battery.level.changed',metadata:{batteryLevel:18,charging:false}} },
  { template:'charging-companion', slug:'charging-companion', title:'Charging Companion', scenario:{event:'battery.charging.started',metadata:{batteryLevel:65,charging:true}} },
  { template:'floating-card', slug:'custom-floating-card', title:'Custom Floating Card Workflow', scenario:{event:'mod.manual.trigger',metadata:{}} }
] as const;

for (const item of definitions) {
  const project = createProject(item.title,item.template);
  project.id = `template-${item.slug}`;
  const projectFile = resolve(examples,`${item.slug}.doomproject.json`);
  writeFileSync(projectFile,JSON.stringify(project,null,2)+'\n');
  const source = resolve(exportsDir,item.slug);
  mkdirSync(source,{recursive:true});
  const compiled = compileProject(project);
  const problems = validateProjectShape(project);
  if(problems.length) throw new Error(`${item.slug}: ${problems.join('; ')}`);
  writeFileSync(resolve(source,'doom.json'),JSON.stringify(compiled.manifest,null,2)+'\n');
  writeFileSync(resolve(source,'mod.json'),JSON.stringify(compiled.definition,null,2)+'\n');
  execFileSync(process.execPath,[cli,'validate',source],{stdio:'inherit'});
  const suite={formatVersion:1,scenarios:[{id:`${item.slug}-smoke`,event:item.scenario,
    androidPermissions:project.manifest.capabilities.includes('overlay.render')?['SYSTEM_ALERT_WINDOW']:[],
    expected:{conditionResult:true,actionSequence:project.definition.triggers[0].actions.map(a=>`${project.definition.triggers[0].id}:${a.id}`),suppressedActions:[]}}]};
  const suitePath=resolve(examples,`${item.slug}.scenarios.json`);writeFileSync(suitePath,JSON.stringify(suite,null,2)+'\n');
  execFileSync(process.execPath,[cli,'test',source,suitePath],{stdio:'inherit'});
  const archive=`${source}.doommod`;rmSync(archive,{force:true});
  execFileSync(process.execPath,[cli,'package',source],{stdio:'inherit'});
  execFileSync(process.execPath,[cli,'validate',archive],{stdio:'inherit'});
  copyFileSync(archive,resolve(kotlinFixtures,`builder-${item.slug}.doommod`));
  const bytes=readFileSync(archive);
  if(bytes.length===0)throw new Error(`Empty package generated for ${item.slug}.`);
}
