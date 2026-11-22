import { useEffect, useMemo, useRef, useState } from 'react';
import { ReactFlow, Background, Controls, MiniMap, applyNodeChanges, addEdge, type Connection, type Edge, type Node, type NodeChange, type ReactFlowInstance } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Activity, ArrowDown, ArrowUp, Check, ChevronDown, CircleHelp, Copy, Download, FolderOpen, Layers3, Play, Plus, Redo2, Save, Settings2, TestTube2, Trash2, Undo2, WandSparkles, X } from 'lucide-react';
import { type Action, type BuilderProject, type Condition, type Setting, type Trigger, capabilityNames, createProject, isAllowedConnection, nodeId, validateProjectShape } from '../lib/project';
import { pushHistory, redoHistory, undoHistory, type History } from '../lib/history';
import { callSdk, decodePackageBase64, downloadBytes, saveProjectFile } from '../lib/sdk';
import capabilityRegistry from '../../../packages/manifest-schema/v2/registry.json';

type Saved = { id: string; title: string; project: BuilderProject };
type Selection = { kind:'metadata' } | { kind:'trigger'; triggerId:string } | { kind:'condition'; triggerId:string } | { kind:'action'; triggerId:string; actionId:string } | { kind:'setting'; settingId:string };
const STORE='doom.visualBuilder.projects.v1';
const templates=[['battery-glow','Battery Glow'],['low-battery','Low Battery Alert'],['charging-companion','Charging Companion'],['floating-card','Floating-card workflow']] as const;
const events=['battery.charging.started','battery.charging.stopped','battery.level.changed','mod.manual.trigger'];
const actionCapabilities: Record<string, string> = capabilityRegistry.actions;
const effects=Object.keys(actionCapabilities);
const settingTypes: Setting['type'][]=['boolean','integer','number','color','enum','text','duration'];

function readProjects(): Saved[] { try { const value=JSON.parse(localStorage.getItem(STORE) ?? '[]'); return Array.isArray(value)?value:[]; } catch { return []; } }
function nodeLabel(condition: Condition): string { return condition.op === 'compare' ? `${condition.source} ${condition.comparison} ${condition.value}` : `${condition.op.toUpperCase()} (${condition.conditions.length})`; }
function graph(project: BuilderProject): { nodes:Node[]; edges:Edge[] } {
  const nodes:Node[]=[]; const edges:Edge[]=[];
  project.definition.triggers.forEach((trigger, ti)=>{
    const eventId=nodeId(trigger.id,'event'), condId=nodeId(trigger.id,'condition');
    nodes.push({ id:eventId, position:project.positions[eventId]??{x:70,y:ti*310+70}, data:{label:`EVENT · ${trigger.event}`, kind:'trigger', triggerId:trigger.id}, type:'default', className:'node-event' });
    nodes.push({ id:condId, position:project.positions[condId]??{x:410,y:ti*310+70}, data:{label:`CONDITION · ${nodeLabel(trigger.condition)}`,kind:'condition',triggerId:trigger.id}, className:'node-condition' });
    edges.push({ id:`${eventId}->${condId}`, source:eventId,target:condId,animated:true });
    trigger.actions.forEach((action, ai)=>{
      const id=nodeId(trigger.id,'action',action.id);
      nodes.push({ id,position:project.positions[id]??{x:790,y:ti*310+ai*145+30},data:{label:`ACTION ${ai+1} · ${action.effect}\n${action.template??'No template'}`,kind:'action',triggerId:trigger.id,actionId:action.id},className:'node-action' });
      edges.push({ id:`${condId}->${id}`,source:condId,target:id });
    });
  });
  return {nodes,edges};
}

function newScenario(project:BuilderProject) {
  const trigger=project.definition.triggers[0];
  const event=trigger?.event??'mod.manual.trigger';
  const expected=trigger?.actions.map(a=>`${trigger.id}:${a.id}`)??[];
  return { formatVersion:1, scenarios:[{ id:'builder-preview',event:{event,metadata:{batteryLevel:18,charging:event==='battery.charging.started'}},
    androidPermissions:project.manifest.capabilities.includes('overlay.render')?['SYSTEM_ALERT_WINDOW']:[],
    expected:{conditionResult:true,actionSequence:expected,suppressedActions:[]} }] };
}

export function App() {
  const [saved,setSaved]=useState<Saved[]>(readProjects);
  const [project,setProject]=useState<BuilderProject>(()=>readProjects()[0]?.project??createProject('New DOOM Mod'));
  const [selection,setSelection]=useState<Selection>({kind:'metadata'});
  const [panel,setPanel]=useState<'validate'|'test'|'export'|null>(null);
  const [messages,setMessages]=useState<string[]>([]);
  const [scenario,setScenario]=useState<any>(()=>newScenario(project));
  const [busy,setBusy]=useState(false);
  const [zoom,setZoom]=useState<ReactFlowInstance|null>(null);
  const [edges,setEdges]=useState<Edge[]>(()=>graph(project).edges);
  const [nodes,setNodes]=useState<Node[]>(()=>graph(project).nodes);
  const [history,setHistory]=useState<History<BuilderProject>>({past:[],future:[]});
  const projectImportRef=useRef<HTMLInputElement>(null);

  useEffect(()=>{ localStorage.setItem(STORE,JSON.stringify(saved)); },[saved]);
  useEffect(()=>{ setSaved(current=>current.some(item=>item.id===project.id)?current.map(item=>item.id===project.id?{...item,title:project.title,project}:item):[{id:project.id,title:project.title,project},...current]); },[project]);
  useEffect(()=>{ const g=graph(project); setNodes(current=>g.nodes.map(node=>{const old=current.find(item=>item.id===node.id); return old?{...node,position:old.position}:node;})); setEdges(g.edges); },[project]);
  const currentErrors=useMemo(()=>validateProjectShape(project),[project]);
  const selectedTrigger=selection.kind==='trigger'||selection.kind==='condition'||selection.kind==='action'?project.definition.triggers.find(t=>t.id===selection.triggerId):undefined;

  const update=(fn:(current:BuilderProject)=>BuilderProject)=>{
    setHistory(stack=>pushHistory(stack,project)); setProject(current=>fn(current));
  };
  const patchTrigger=(id:string,fn:(trigger:Trigger)=>Trigger)=>update(p=>({...p,definition:{...p.definition,triggers:p.definition.triggers.map(t=>t.id===id?fn(t):t)}}));
  const patchAction=(triggerId:string,actionId:string,fn:(action:Action)=>Action)=>patchTrigger(triggerId,t=>({...t,actions:t.actions.map(a=>a.id===actionId?fn(a):a)}));
  const addTrigger=()=>{
    const id=`trigger-${project.definition.triggers.length+1}`;
    const item:Trigger={id,event:'mod.manual.trigger',condition:{op:'all',conditions:[{op:'compare',source:'event.name',comparison:'equals',value:'mod.manual.trigger'}]},actions:[{id:'preview-card',effect:'ui.preview.show',template:'floating-card',parameters:{message:'Hello from DOOM',durationMs:3000}}]};
    update(p=>({...p,manifest:{...p.manifest,capabilities:[...new Set([...p.manifest.capabilities,'ui.preview'])]},definition:{...p.definition,triggers:[...p.definition.triggers,item]}})); setSelection({kind:'trigger',triggerId:id});
  };
  const addAction=(triggerId:string)=>{
    const id=`action-${Date.now().toString(36)}`;
    patchTrigger(triggerId,t=>({...t,actions:[...t.actions,{id,effect:'ui.preview.show',template:'floating-card',parameters:{message:'New action',durationMs:3000}}]}));
    setSelection({kind:'action',triggerId,actionId:id});
  };
  const removeSelection=()=>{
    if(selection.kind==='trigger') update(p=>({...p,definition:{...p.definition,triggers:p.definition.triggers.filter(t=>t.id!==selection.triggerId)}}));
    if(selection.kind==='action') patchTrigger(selection.triggerId,t=>({...t,actions:t.actions.filter(a=>a.id!==selection.actionId)}));
    if(selection.kind==='setting') update(p=>{const settings={...p.definition.settings}; delete settings[selection.settingId]; return {...p,definition:{...p.definition,settings}};});
    setSelection({kind:'metadata'});
  };
  const undo=()=>{ const result=undoHistory(history,project);if(result){setHistory(result.history);setProject(result.current);} };
  const redo=()=>{ const result=redoHistory(history,project);if(result){setHistory(result.history);setProject(result.current);} };
  useEffect(()=>{ const handler=(e:KeyboardEvent)=>{ if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'){e.preventDefault();e.shiftKey?redo():undo();} else if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='y'){e.preventDefault();redo();} else if(e.key==='Delete'&&!(e.target instanceof HTMLInputElement)&&!(e.target instanceof HTMLTextAreaElement)) removeSelection(); };window.addEventListener('keydown',handler);return()=>window.removeEventListener('keydown',handler); });

  const createFrom=(template:string)=>{ const label=templates.find(([key])=>key===template)?.[1]??'New DOOM Mod'; const next=createProject(label,template); setProject(next);setSelection({kind:'metadata'});setScenario(newScenario(next)); };
  const renameProject=()=>{ const name=prompt('Project name',project.title); if(name?.trim()) update(p=>({...p,title:name.trim(),manifest:{...p.manifest,name:name.trim()}})); };
  const duplicateProject=()=>{ const next=structuredClone(project) as BuilderProject;next.id=crypto.randomUUID();next.title=`${project.title} copy`;next.manifest={...next.manifest,id:`local.copy${Date.now().toString(36)}`,name:next.title};setProject(next); };
  const importProject=async(file:File)=>{try{const parsed=JSON.parse(await file.text());if(parsed.projectFormatVersion!==1||!parsed.manifest||parsed.definition?.definitionVersion!==4)throw new Error('Unsupported project format. Only builder project format 1 with Definition 4 can be imported.');setProject(parsed as BuilderProject);setSelection({kind:'metadata'});setScenario(newScenario(parsed));}catch(error){setMessages([error instanceof Error?error.message:'Could not import project.']);setPanel('validate');}};
  const installTemplate=async(template:string)=>createFrom(template);

  const validate=async()=>{setBusy(true);setPanel('validate');setMessages(currentErrors);try{if(!currentErrors.length){const result=await callSdk('validate',project);setMessages(result.ok?[`PASS · ${result.output?.trim()??'Validated by the DOOM SDK.'}`]:[result.error??'Validation failed.']);}}catch(error){setMessages([error instanceof Error?error.message:'Local SDK service unavailable. Start the builder with npm run dev.']);}finally{setBusy(false);}};
  const simulate=async()=>{setBusy(true);setPanel('test');try{const result=await callSdk('test',project,scenario);setMessages((result.output??'').trim().split('\n').concat(result.error?[result.error]:[]));}catch(error){setMessages([error instanceof Error?error.message:'Local test simulator unavailable. Start the builder with npm run dev.']);}finally{setBusy(false);}};
  const exportPackage=async()=>{setBusy(true);setPanel('export');try{const result=await callSdk('package',project);if(!result.ok||!result.archive){setMessages([result.error??'Package validation failed.']);return;}const filename=`${project.manifest.id}.doommod`;downloadBytes(filename,decodePackageBase64(result.archive),'application/zip');setMessages([`Exported ${filename}.`,result.output?.trim()??'Validated and packaged by the DOOM CLI.']);}catch(error){setMessages([error instanceof Error?error.message:'Package export requires the local SDK service. Start the builder with npm run dev.']);}finally{setBusy(false);}};
  const addSetting=()=>{const base=`setting${Object.keys(project.definition.settings).length+1}`;let id=base;let suffix=1;while(project.definition.settings[id])id=`${base}${suffix++}`;const item:Setting={type:'boolean',label:'New setting',default:false};update(p=>({...p,definition:{...p.definition,settings:{...p.definition.settings,[id]:item}}}));setSelection({kind:'setting',settingId:id});};

  const onNodesChange=(changes:NodeChange<Node>[])=>{setNodes(current=>applyNodeChanges(changes,current));const positions=changes.filter((c):c is Extract<NodeChange<Node>,{type:'position'}>=>c.type==='position'&&!!c.position);if(positions.length)update(p=>({...p,positions:{...p.positions,...Object.fromEntries(positions.map(c=>[c.id,c.position!]))}}));};
  const isValidConnection=(connection:Connection|Edge)=>{
    const source=nodes.find(n=>n.id===connection.source),target=nodes.find(n=>n.id===connection.target);
    return !!source&&!!target&&isAllowedConnection(String(source.data.kind),String(target.data.kind),source.data.triggerId===target.data.triggerId,source.id===target.id);
  };
  const onConnect=(connection:Connection)=>{if(isValidConnection(connection))setEdges(current=>addEdge({...connection,animated:connection.source?.includes('/event')},current));};
  const chooseNode=(node:Node)=>{const data=node.data as any;if(data.kind==='trigger')setSelection({kind:'trigger',triggerId:data.triggerId});else if(data.kind==='condition')setSelection({kind:'condition',triggerId:data.triggerId});else setSelection({kind:'action',triggerId:data.triggerId,actionId:data.actionId});};
  const downloadProject=()=>saveProjectFile(project);

  return <div className="app-shell">
    <header className="topbar"><div className="brand"><div className="brand-mark">D</div><div><strong>DOOM</strong><span>VISUAL MOD BUILDER</span></div></div><div className="project-title"><span className="live-dot"/>{project.title}<button onClick={renameProject} className="icon-button" title="Rename project">⌄</button></div><div className="toolbar">
      <button className="tool" onClick={undo} disabled={!history.past.length} title="Undo (Ctrl+Z)"><Undo2 size={16}/></button><button className="tool" onClick={redo} disabled={!history.future.length} title="Redo (Ctrl+Shift+Z)"><Redo2 size={16}/></button><i/>
      <button className="tool" onClick={downloadProject}><Save size={16}/> Save project</button><button className="tool" onClick={()=>projectImportRef.current?.click()}><FolderOpen size={16}/> Open</button>
      <button className="tool" onClick={validate}><Check size={16}/> Validate</button><button className="tool" onClick={()=>{setPanel('test');setScenario(newScenario(project));}}><TestTube2 size={16}/> Test</button>
      <button className="primary" onClick={exportPackage} disabled={busy}><Download size={16}/> Export .doommod</button>
    </div></header>
    <div className="workspace">
      <aside className="left-rail"><div className="rail-head"><span>PROJECTS</span><button className="small-icon" title="New project" onClick={()=>createFrom('blank')}><Plus size={15}/></button></div>
        <div className="project-list">{saved.map(item=><button key={item.id} className={`project-item ${item.id===project.id?'active':''}`} onClick={()=>{setProject(item.project);setSelection({kind:'metadata'});}}><span className="project-glyph">◇</span><span>{item.title}</span><span className="project-more" onClick={e=>{e.stopPropagation();setProject(item.project);setTimeout(duplicateProject,0);}}>•••</span></button>)}</div>
        <div className="rail-divider"/><div className="rail-head"><span>START FROM TEMPLATE</span></div>{templates.map(([id,label])=><button className="template-link" key={id} onClick={()=>installTemplate(id)}><WandSparkles size={14}/>{label}</button>)}
        <div className="rail-footer"><div className="api-badge">SDK API 2 <span>·</span> DEFINITION 4</div><span>Local project autosave enabled</span></div>
      </aside>
      <main className="main-area"><div className="canvas-toolbar"><div className="canvas-heading"><Layers3 size={17}/><div><b>Workflow canvas</b><span>Drag nodes to arrange · connect event → condition → action</span></div></div><div className="canvas-actions"><button onClick={addTrigger}><Plus size={15}/> Trigger</button><button onClick={()=>selectedTrigger&&addAction(selectedTrigger.id)} disabled={!selectedTrigger}><Plus size={15}/> Action</button><button onClick={addSetting}><Settings2 size={15}/> Setting</button><button onClick={()=>zoom?.fitView({padding:0.2})}>Fit view</button></div></div>
        <div className="canvas-wrap"><ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={()=>{}} onConnect={onConnect} isValidConnection={isValidConnection} onNodeClick={(_event,node)=>chooseNode(node)} onPaneClick={()=>setSelection({kind:'metadata'})} onInit={setZoom} fitView deleteKeyCode={null} nodesConnectable elementsSelectable onlyRenderVisibleElements><Background color="#24242b" gap={24}/><MiniMap pannable zoomable/><Controls/></ReactFlow>{project.definition.triggers.length===0&&<div className="empty-canvas"><div className="empty-orb"><Activity/></div><h2>Start with an event</h2><p>Build a supported declarative workflow from registered DOOM events, conditions and actions.</p><button className="primary" onClick={addTrigger}><Plus size={16}/> Add first trigger</button></div>}</div>
        <div className="bottom-bar"><span><span className={`status-dot ${currentErrors.length?'warning':''}`}/>{currentErrors.length?`${currentErrors.length} issue${currentErrors.length===1?'':'s'} need attention`:'All changes saved locally'}</span><span>{project.definition.triggers.length} triggers <b>·</b> {project.definition.triggers.reduce((n,t)=>n+t.actions.length,0)} actions <b>·</b> {Object.keys(project.definition.settings).length} settings</span><button onClick={()=>setPanel(panel?'validate':'validate')}><CircleHelp size={14}/> Builder help</button></div>
      </main>
      <aside className="inspector"><div className="inspector-head"><div><span>PROPERTIES</span><h3>{selection.kind==='metadata'?'Mod metadata':selection.kind==='setting'?'Setting designer':selection.kind==='action'?'Action properties':selection.kind==='condition'?'Condition editor':'Trigger properties'}</h3></div>{selection.kind!=='metadata'&&<button className="danger-icon" onClick={removeSelection} title="Delete selected item"><Trash2 size={15}/></button>}</div>
        {selection.kind==='metadata'?<MetadataForm project={project} update={update}/>:null}
        {selection.kind==='trigger'&&selectedTrigger?<TriggerForm trigger={selectedTrigger} patch={fn=>patchTrigger(selectedTrigger.id,fn)}/>:null}
        {selection.kind==='condition'&&selectedTrigger?<ConditionForm condition={selectedTrigger.condition} settings={project.definition.settings} onChange={condition=>patchTrigger(selectedTrigger.id,t=>({...t,condition}))}/>:null}
        {selection.kind==='action'&&selectedTrigger?<ActionForm action={selectedTrigger.actions.find(a=>a.id===selection.actionId)!} settings={project.definition.settings} patch={fn=>patchAction(selectedTrigger.id,selection.actionId,fn)} move={direction=>patchTrigger(selectedTrigger.id,t=>{const actions=[...t.actions],i=actions.findIndex(a=>a.id===selection.actionId),j=i+direction;if(j<0||j>=actions.length)return t;[actions[i],actions[j]]=[actions[j],actions[i]];return {...t,actions};})}/>:null}
        {selection.kind==='setting'?<SettingForm id={selection.settingId} setting={project.definition.settings[selection.settingId]} patch={fn=>update(p=>({...p,definition:{...p.definition,settings:{...p.definition.settings,[selection.settingId]:fn(p.definition.settings[selection.settingId])}}}))}/>:null}
        {selection.kind==='metadata'&&<div className="inspector-bottom-actions"><button onClick={duplicateProject}><Copy size={14}/> Duplicate project</button><button className="danger" onClick={()=>{if(confirm('Delete this local project?')){const next=saved.filter(s=>s.id!==project.id);setSaved(next);setProject(next[0]?.project??createProject('New DOOM Mod'));}}}><Trash2 size={14}/> Delete project</button></div>}
      </aside>
    </div>
    {panel&&<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setPanel(null);}}><section className="tool-panel"><header><div><span className="panel-eyebrow">LOCAL SDK TOOLS</span><h2>{panel==='validate'?'Validation results':panel==='test'?'Scenario simulator':'Package export'}</h2></div><button className="icon-button" onClick={()=>setPanel(null)}><X size={18}/></button></header>
      {panel==='validate'&&<div className="panel-content"><p>Project checks and the existing DOOM CLI validator run against the canonical Manifest 3 / Definition 4 schemas.</p><button className="primary" onClick={validate} disabled={busy}><Check size={15}/> Run validation</button><ResultList messages={messages}/></div>}
      {panel==='test'&&<div className="panel-content"><p>Desktop simulation records approved actions only. It does not show Android overlays or read battery hardware.</p><ScenarioForm scenario={scenario} setScenario={setScenario} project={project}/><button className="primary" onClick={simulate} disabled={busy}><Play size={15}/> Run scenario</button><ResultList messages={messages}/></div>}
      {panel==='export'&&<div className="panel-content"><p>Export is performed by the existing DOOM CLI ZIP writer after strict schema and package validation.</p><div className="export-summary"><b>{project.manifest.name}</b><span>{project.manifest.id} · {project.manifest.version}</span><span>{project.manifest.capabilities.length} declared capabilities · all grants remain user controlled</span></div><button className="primary" onClick={exportPackage} disabled={busy}><Download size={15}/> Validate and download package</button><ResultList messages={messages}/></div>}
    </section></div>}
    <input ref={projectImportRef} hidden type="file" accept=".doomproject.json,application/json" onChange={e=>{const file=e.target.files?.[0];if(file)void importProject(file);e.currentTarget.value='';}}/>
  </div>;
}

function Field({label,children,help}:{label:string;children:React.ReactNode;help?:string}){return <label className="field"><span>{label}</span>{children}{help&&<small>{help}</small>}</label>}
function TextInput(props:React.InputHTMLAttributes<HTMLInputElement>){return <input className="text-input" {...props}/>}
function Select({value,onChange,options}:{value:string;onChange:(value:string)=>void;options:string[]}){return <div className="select-wrap"><select className="text-input" value={value} onChange={e=>onChange(e.target.value)}>{options.map(x=><option key={x} value={x}>{x}</option>)}</select><ChevronDown size={14}/></div>}

function MetadataForm({project,update}:{project:BuilderProject;update:(fn:(p:BuilderProject)=>BuilderProject)=>void}){
  const set=(key:keyof BuilderProject['manifest'],value:any)=>update(p=>({...p,manifest:{...p.manifest,[key]:value}}));
  return <div className="form-scroll"><div className="form-section"><h4>Identity</h4><Field label="Display name"><TextInput value={project.manifest.name} onChange={e=>set('name',e.target.value)}/></Field><Field label="Package ID" help="Stable ID, lowercase and unique when installed."><TextInput value={project.manifest.id} onChange={e=>set('id',e.target.value)}/></Field><div className="two-col"><Field label="Version"><TextInput value={project.manifest.version} onChange={e=>set('version',e.target.value)}/></Field><Field label="License"><TextInput value={project.manifest.license} onChange={e=>set('license',e.target.value)}/></Field></div><Field label="Description"><textarea className="text-input textarea" value={project.manifest.description} onChange={e=>set('description',e.target.value)}/></Field><Field label="Author"><TextInput value={project.manifest.author} onChange={e=>set('author',e.target.value)}/></Field><Field label="Minimum Android API"><TextInput type="number" min={31} value={project.manifest.minAndroidApi} onChange={e=>set('minAndroidApi',Number(e.target.value))}/></Field></div>
  <div className="form-section"><h4>Requested capabilities</h4><p className="muted">Selecting a capability declares a request. DOOM users still approve each capability before activation.</p>{Object.entries(capabilityNames).map(([id,name])=><label className="check-row" key={id}><input type="checkbox" checked={project.manifest.capabilities.includes(id)} onChange={e=>update(p=>({...p,manifest:{...p.manifest,capabilities:e.target.checked?[...p.manifest.capabilities,id]:p.manifest.capabilities.filter(c=>c!==id)}}))}/><span><b>{name}</b><small>{id}</small></span></label>)}</div></div>;
}
function TriggerForm({trigger,patch}:{trigger:Trigger;patch:(fn:(t:Trigger)=>Trigger)=>void}){
  return <div className="form-scroll"><div className="form-section"><Field label="Trigger ID"><TextInput value={trigger.id} onChange={e=>patch(t=>({...t,id:e.target.value}))}/></Field><Field label="Event"><Select value={trigger.event} options={events} onChange={event=>patch(t=>({...t,event}))}/></Field><p className="muted">{trigger.event==='mod.manual.trigger'?'Run from an explicit in-app test action.':`Requires ${trigger.event.startsWith('battery.')?'battery.read':'registered capability'}.`}</p></div><div className="form-section"><h4>Workflow policy</h4><Field label="Execution mode"><Select value={trigger.workflow?.executionMode??'sequential'} options={['sequential','parallel']} onChange={value=>patch(t=>({...t,workflow:{id:t.workflow?.id??`${t.id}-workflow`,executionMode:value as any,failurePolicy:t.workflow?.failurePolicy??'stopOnFailure',timeoutMs:t.workflow?.timeoutMs??10000,maxConcurrency:t.workflow?.maxConcurrency??2}}))}/></Field><Field label="Failure policy"><Select value={trigger.workflow?.failurePolicy??'stopOnFailure'} options={['stopOnFailure','continueOnFailure']} onChange={value=>patch(t=>({...t,workflow:{id:t.workflow?.id??`${t.id}-workflow`,executionMode:t.workflow?.executionMode??'sequential',failurePolicy:value as any,timeoutMs:t.workflow?.timeoutMs??10000,maxConcurrency:t.workflow?.maxConcurrency??2}}))}/></Field><Field label="Timeout (ms)"><TextInput type="number" min={1} max={30000} value={trigger.workflow?.timeoutMs??10000} onChange={e=>patch(t=>({...t,workflow:{id:t.workflow?.id??`${t.id}-workflow`,executionMode:t.workflow?.executionMode??'sequential',failurePolicy:t.workflow?.failurePolicy??'stopOnFailure',timeoutMs:Number(e.target.value),maxConcurrency:t.workflow?.maxConcurrency??2}}))}/></Field></div><button className="subtle-button" onClick={()=>{const id=`action-${trigger.actions.length+1}`;patch(t=>({...t,actions:[...t.actions,{id,effect:'ui.preview.show',template:'floating-card',parameters:{message:'New action',durationMs:3000}}]}));}}><Plus size={14}/> Add ordered action</button></div>;
}
function ConditionForm({condition,settings,onChange}:{condition:Condition;settings:Record<string,Setting>;onChange:(condition:Condition)=>void}){
  const patchAt=(path:number[],next:Condition|null)=>{
    const replace=(node:Condition,depth:number):Condition=>{
      if(depth===path.length)return next??node;
      if(node.op==='compare')return node;
      const index=path[depth];const children=[...node.conditions];
      if(depth===path.length-1&&next===null)children.splice(index,1);else children[index]=replace(children[index],depth+1);
      return {...node,conditions:children};
    };
    onChange(replace(condition,0));
  };
  const render=(node:Condition,path:number[]):React.ReactNode=>node.op==='compare'?<div className="condition-editor" key={path.join('-')}>
    <div className="inline-between"><b>Comparison</b>{path.length>0&&<button className="remove-condition" onClick={()=>patchAt(path,null)} title="Remove condition"><X size={13}/></button>}</div>
    <Field label="Value source"><Select value={node.source} options={['battery.level','battery.charging','event.name',...Object.keys(settings).map(id=>`setting.${id}`)]} onChange={source=>patchAt(path,{...node,source,value:source==='battery.charging'||settings[source.slice(8)]?.type==='boolean'?true:source==='event.name'?'mod.manual.trigger':0})}/></Field>
    {(()=>{const setting=node.source.startsWith('setting.')?settings[node.source.slice(8)]:undefined;const booleanValue=node.source==='battery.charging'||setting?.type==='boolean';const stringValue=node.source==='event.name'||setting?.type==='enum'||setting?.type==='text';return <>
      <Field label="Comparison"><Select value={node.comparison} options={booleanValue?['equals','notEquals']:['lessThan','lessThanOrEqual','greaterThan','greaterThanOrEqual','equals','notEquals']} onChange={comparison=>patchAt(path,{...node,comparison})}/></Field>
      <Field label="Compare to">{booleanValue?<Select value={String(node.value)} options={['true','false']} onChange={value=>patchAt(path,{...node,value:value==='true'})}/>:<TextInput type={stringValue?'text':'number'} value={String(node.value)} onChange={e=>patchAt(path,{...node,value:stringValue?e.target.value:Number(e.target.value)})}/>}</Field>
    </>;})()}
    {node.source.startsWith('setting.')&&<p className="warning-note">Settings can be the value source; V4 does not support a setting reference as the expected value.</p>}
  </div>:<div className="condition-group" key={path.join('-')}>
    <div className="group-heading"><Select value={node.op} options={['all','any']} onChange={op=>patchAt(path,{...node,op:op as 'all'|'any'})}/>{path.length>0&&<button className="remove-condition" onClick={()=>patchAt(path,null)} title="Remove group"><X size={13}/></button>}</div>
    <p className="muted">Every child follows the selected {node.op.toUpperCase()} rule.</p>
    {node.conditions.map((child,index)=><div className="condition-child" key={index}>{render(child,[...path,index])}</div>)}
    <div className="inline-buttons"><button className="subtle-button" onClick={()=>patchAt(path,{...node,conditions:[...node.conditions,{op:'compare',source:'battery.level',comparison:'lessThanOrEqual',value:20}]})}><Plus size={13}/> Add comparison</button><button className="subtle-button" onClick={()=>patchAt(path,{...node,conditions:[...node.conditions,{op:'all',conditions:[{op:'compare',source:'event.name',comparison:'equals',value:'mod.manual.trigger'}]}]})}><Plus size={13}/> Add nested group</button></div>
  </div>;
  return <div className="form-scroll"><div className="form-section"><p className="muted">Nested groups use strict Definition 4 ALL / ANY semantics. Unsupported setting comparison references are called out.</p>{render(condition,[])}</div></div>;
}
function ActionForm({action,settings,patch,move}:{action:Action;settings:Record<string,Setting>;patch:(fn:(a:Action)=>Action)=>void;move:(direction:number)=>void}){
  const params=action.parameters??{};const allowed=action.effect==='overlay.dismiss'||action.effect==='device.display.read'?[]:action.effect==='haptics.pulse'?['durationMs']:action.template==='battery-ring'?['color','intensity','ringThicknessDp','sizeDp','durationMs','position']:['title','message','color','durationMs'];
  const cap=actionCapabilities[action.effect];
  return <div className="form-scroll"><div className="form-section"><div className="inline-between"><h4>Action</h4><div className="move-buttons"><button title="Move earlier" onClick={()=>move(-1)}><ArrowUp size={14}/></button><button title="Move later" onClick={()=>move(1)}><ArrowDown size={14}/></button></div></div><Field label="Registered action"><Select value={action.effect} options={effects} onChange={effect=>patch(a=>({...a,effect,template:effect==='overlay.dismiss'||effect==='haptics.pulse'||effect==='device.display.read'?undefined:a.template??'floating-card'}))}/></Field>{(action.effect==='overlay.show'||action.effect==='overlay.preset.apply'||action.effect==='ui.preview.show')&&<Field label="Approved template / preset"><Select value={action.template??'floating-card'} options={['battery-ring','floating-card']} onChange={template=>patch(a=>({...a,template}))}/></Field>}<p className="capability-note">Requires <b>{cap}</b>. DOOM will check approval and Android prerequisites at runtime.</p></div>{allowed.length>0&&<div className="form-section"><h4>Parameters</h4>{allowed.map(key=>{const value=params[key]??(key==='durationMs'?3000:key==='color'?'#A78BFA':'');const settingRef=typeof value==='object'?value.$setting:'';return <div className="parameter" key={key}><Field label={key}>{settingRef?<Select value={settingRef} options={Object.keys(settings)} onChange={id=>patch(a=>({...a,parameters:{...params,[key]:{$setting:id}}}))}/>:<TextInput value={String(value)} type={['durationMs','intensity','ringThicknessDp','sizeDp'].includes(key)?'number':'text'} onChange={e=>patch(a=>({...a,parameters:{...params,[key]:['durationMs','intensity','ringThicknessDp','sizeDp'].includes(key)?Number(e.target.value):e.target.value}}))}/>}</Field><Field label="Bind setting"><Select value={settingRef||'(literal)'} options={['(literal)',...Object.keys(settings)]} onChange={id=>patch(a=>({...a,parameters:{...params,[key]:id==='(literal)'?(typeof value==='object'?'':value):{$setting:id}}}))}/></Field></div>;})}</div>}</div>;
}
function SettingForm({id,setting,patch}:{id:string;setting:Setting;patch:(fn:(s:Setting)=>Setting)=>void}){
  if(!setting)return <div className="form-scroll muted">Setting was removed.</div>;
  return <div className="form-scroll"><div className="form-section"><Field label="Setting ID"><TextInput value={id} readOnly/></Field><Field label="Control type"><Select value={setting.type} options={settingTypes} onChange={type=>patch(s=>({...s,type:type as Setting['type'],default:type==='boolean'?false:type==='color'?'#A78BFA':type==='text'?'':type==='enum'?(s.values?.[0]??'first'):0,...(['integer','number','duration'].includes(type)?{min:0,max:100,step:1}:{}),...(type==='duration'?{unit:'ms'}:{}),...(type==='text'?{maxLength:64}:{}),...(type==='enum'?{values:['first','second']}:{} )}))}/></Field><Field label="Label"><TextInput value={setting.label} maxLength={48} onChange={e=>patch(s=>({...s,label:e.target.value}))}/></Field>{setting.description!==undefined&&<Field label="Description"><TextInput value={setting.description} maxLength={120} onChange={e=>patch(s=>({...s,description:e.target.value}))}/></Field>}
    {setting.type==='boolean'?<Field label="Default"><Select value={String(setting.default)} options={['true','false']} onChange={v=>patch(s=>({...s,default:v==='true'}))}/></Field>:null}
    {['integer','number','duration'].includes(setting.type)&&<><Field label="Default"><TextInput type="number" value={Number(setting.default)} onChange={e=>patch(s=>({...s,default:Number(e.target.value)}))}/></Field><div className="three-col"><Field label="Minimum"><TextInput type="number" value={setting.min??0} onChange={e=>patch(s=>({...s,min:Number(e.target.value)}))}/></Field><Field label="Maximum"><TextInput type="number" value={setting.max??100} onChange={e=>patch(s=>({...s,max:Number(e.target.value)}))}/></Field><Field label="Step"><TextInput type="number" value={setting.step??1} onChange={e=>patch(s=>({...s,step:Number(e.target.value)}))}/></Field></div>{setting.type==='duration'&&<Field label="Unit"><Select value={setting.unit??'ms'} options={['ms','s','min']} onChange={unit=>patch(s=>({...s,unit:unit as any}))}/></Field>}</>}
    {setting.type==='color'&&<Field label="Default color"><div className="color-row"><input type="color" value={typeof setting.default==='string'&&/^#[a-f0-9]{6}$/i.test(setting.default)?setting.default:'#A78BFA'} onChange={e=>patch(s=>({...s,default:e.target.value}))}/><TextInput value={String(setting.default)} onChange={e=>patch(s=>({...s,default:e.target.value}))}/></div></Field>}
    {setting.type==='enum'&&<><Field label="Choices (one per line)"><textarea className="text-input textarea" value={(setting.values??[]).join('\n')} onChange={e=>patch(s=>({...s,values:e.target.value.split('\n').filter(Boolean)}))}/></Field><Field label="Default"><Select value={String(setting.default)} options={setting.values??[]} onChange={v=>patch(s=>({...s,default:v}))}/></Field></>}
    {setting.type==='text'&&<><Field label="Default text"><TextInput value={String(setting.default)} maxLength={setting.maxLength??256} onChange={e=>patch(s=>({...s,default:e.target.value}))}/></Field><Field label="Maximum length"><TextInput type="number" min={1} max={256} value={setting.maxLength??64} onChange={e=>patch(s=>({...s,maxLength:Number(e.target.value)}))}/></Field></>}
  </div><div className="preview-box"><span>LIVE CONTROL PREVIEW</span>{setting.type==='boolean'?<label className="check-row"><input type="checkbox" checked={!!setting.default} readOnly/><span>{setting.label}</span></label>:setting.type==='color'?<div className="color-swatch" style={{background:String(setting.default)}}>{String(setting.default)}</div>:setting.type==='enum'?<Select value={String(setting.default)} options={setting.values??[]} onChange={()=>{}}/>:<input className="text-input" type={setting.type==='text'?'text':'range'} min={setting.min} max={setting.max} step={setting.step} maxLength={setting.maxLength} value={String(setting.default)} readOnly/>}</div></div>;
}
function ScenarioForm({scenario,setScenario,project}:{scenario:any;setScenario:(s:any)=>void;project:BuilderProject}){
  const item=scenario.scenarios[0];
  const set=(patch:any)=>setScenario({...scenario,scenarios:[{...item,...patch}]});
  return <div className="scenario-grid">
    <Field label="Input event"><Select value={item.event.event} options={[...new Set([...events,...project.definition.triggers.map(t=>t.event)])]} onChange={event=>set({event:{...item.event,event}})}/></Field>
    <div className="two-col">
      <Field label="Battery level"><TextInput type="number" min={0} max={100} value={item.event.metadata.batteryLevel??50} onChange={e=>set({event:{...item.event,metadata:{...item.event.metadata,batteryLevel:Number(e.target.value)}}})}/></Field>
      <Field label="Charging"><Select value={String(item.event.metadata.charging??false)} options={['false','true']} onChange={v=>set({event:{...item.event,metadata:{...item.event.metadata,charging:v==='true'}}})}/></Field>
    </div>
    <Field label="Expected condition"><Select value={String(item.expected.conditionResult)} options={['true','false']} onChange={v=>set({expected:{...item.expected,conditionResult:v==='true'}})}/></Field>
    <Field label="Expected ordered actions (comma separated IDs)"><TextInput value={(item.expected.actionSequence??[]).join(', ')} onChange={e=>set({expected:{...item.expected,actionSequence:e.target.value.split(',').map((x:string)=>x.trim()).filter(Boolean)}})}/></Field>
    <Field label="Capabilities approved for this simulation"><div className="cap-list">{project.manifest.capabilities.map(cap=><label key={cap}><input type="checkbox" checked={(item.grantedCapabilities??project.manifest.capabilities).includes(cap)} onChange={e=>{const current=(item.grantedCapabilities??project.manifest.capabilities) as string[];set({grantedCapabilities:e.target.checked?[...new Set([...current,cap])]:current.filter(c=>c!==cap)});}}/>{cap}</label>)}</div></Field>
    {project.manifest.capabilities.includes('overlay.render')&&<Field label="Android overlay permission"><label className="check-row"><input type="checkbox" checked={(item.androidPermissions??[]).includes('SYSTEM_ALERT_WINDOW')} onChange={e=>set({androidPermissions:e.target.checked?[...new Set([...(item.androidPermissions??[]),'SYSTEM_ALERT_WINDOW'])]:(item.androidPermissions??[]).filter((p:string)=>p!=='SYSTEM_ALERT_WINDOW')})}/><span>Simulate SYSTEM_ALERT_WINDOW approved</span></label></Field>}
    {Object.keys(project.definition.settings).length>0&&<div className="scenario-settings"><b>Initial setting overrides</b>{Object.entries(project.definition.settings).map(([id,spec])=><Field key={id} label={`${spec.label} · ${id}`}>{spec.type==='boolean'?<Select value={String(item.initialSettings?.[id]??spec.default)} options={['true','false']} onChange={value=>set({initialSettings:{...item.initialSettings,[id]:value==='true'}})}/>:spec.type==='enum'?<Select value={String(item.initialSettings?.[id]??spec.default)} options={spec.values??[]} onChange={value=>set({initialSettings:{...item.initialSettings,[id]:value}})}/>:<TextInput type={spec.type==='text'?'text':spec.type==='color'?'text':'number'} value={String(item.initialSettings?.[id]??spec.default)} onChange={e=>set({initialSettings:{...item.initialSettings,[id]:['integer','number','duration'].includes(spec.type)?Number(e.target.value):e.target.value}})}/>}</Field>)}</div>}
    <p className="muted">Android external overlays are suppressed unless SYSTEM_ALERT_WINDOW is present in the scenario permission list.</p>
  </div>;
}
function ResultList({messages}:{messages:string[]}){return messages.length?<div className="result-list">{messages.map((message,i)=><div className={message.startsWith('PASS')||message.startsWith('Exported')?'result-ok':'result-message'} key={i}>{message}</div>)}</div>:null;}
