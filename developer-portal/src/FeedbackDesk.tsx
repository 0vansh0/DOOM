import { useEffect, useState } from 'react';
import { request } from './api';

type Triage = {priority:string;status:string;component:string;reproduction:string;regression_test:string;resolution:string;assignee_id:string|null};
type Feedback = Triage & {id:string;report:Record<string,string>;created_at:string;updated_at:string};
type Reviewer = {id:string;email:string;role:'REVIEWER'|'ADMIN'};
export function FeedbackDesk({token,review}:{token:string;review:boolean}) {
  const [rows,setRows]=useState<Feedback[]>([]);
  const [reviewers,setReviewers]=useState<Reviewer[]>([]);
  const [offset,page]=useState(0),[filter,filterBy]=useState(''),[revision,reload]=useState(0);
  const [draft,setDraft]=useState(''),[consent,approve]=useState(false),[message,notify]=useState('');
  const [busy,setBusy]=useState(false);
  useEffect(()=>{
    const abort=new AbortController();
    setRows([]);
    request<Feedback[]>((review?'/v1/moderation/feedback':'/v1/feedback')+'?offset='+offset+(filter?'&status='+filter:''),token,{signal:abort.signal})
      .then(data=>{if(!abort.signal.aborted)setRows(data)}).catch(()=>{if(!abort.signal.aborted)notify('Could not load feedback.')});
    return ()=>abort.abort();
  },[token,review,offset,filter,revision]);
  useEffect(()=>{
    if(!review){setReviewers([]);return}
    const abort=new AbortController();
    request<Reviewer[]>('/v1/moderation/reviewers',token,{signal:abort.signal})
      .then(setReviewers).catch(()=>{if(!abort.signal.aborted)notify('Could not load active reviewers.')});
    return ()=>abort.abort();
  },[token,review]);
  async function perform(action:()=>Promise<unknown>,success:string){
    setBusy(true);
    try {await action();notify(success);reload(n=>n+1)} catch(e){notify(e instanceof Error?e.message:'Request failed.')} finally{setBusy(false)}
  }
  return <section className="card"><h2>{review?'Issue triage':'Private feedback'}</h2>
    {!review && <><label>Local report.json (64 KiB maximum)<input type="file" accept=".json" onChange={async e=>{
      setDraft('');approve(false);const f=e.target.files?.[0];if(!f)return;
      if(f.size>65536){notify('Report exceeds 64 KiB.');return}
      try{setDraft(await f.text())}catch{notify('Could not read the file.')}
    }}/></label><pre>{draft}</pre><label><input type="checkbox" checked={consent} onChange={e=>approve(e.target.checked)}/> Share this report with DOOM reviewers for up to 30 days</label>
    <button disabled={busy||!consent||!draft} onClick={()=>void perform(async()=>{
      await request('/v1/feedback',token,{method:'POST',body:JSON.stringify({consent,report:JSON.parse(draft)})});approve(false);setDraft('')
    },'Feedback submitted.')}>Submit reviewed report</button></>}
    <label>Status filter<select value={filter} onChange={e=>{filterBy(e.target.value);page(0)}}>{['','open','in-progress','resolved'].map(s=><option key={s} value={s}>{s||'All statuses'}</option>)}</select></label>
    <p role="status">{message}</p>
    {rows.map(r=><article key={r.id}><h3>{r.report.title}</h3><p>{r.id} · {r.status} · {r.priority}</p><p>Created {new Date(r.created_at).toLocaleString()} · Updated {new Date(r.updated_at).toLocaleString()}</p>
      <dl>{Object.entries(r.report).map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
      {review?<form onSubmit={e=>{e.preventDefault();const values=Object.fromEntries(new FormData(e.currentTarget)) as Record<string,string|null>;
        values.assignee_id=values.assignee_id||null;
        void perform(()=>request('/v1/moderation/feedback/'+r.id,token,{method:'POST',body:JSON.stringify(values)}),'Triage saved.')}}>
        {Object.entries({priority:['P0','P1','P2','P3'],status:['open','in-progress','resolved'],reproduction:['unverified','reproduced','cannot reproduce']}).map(([k,options])=><label key={k}>{k}<select name={k} defaultValue={String(r[k as keyof Triage]??'')}>{options.map(o=><option key={o}>{o}</option>)}</select></label>)}
        <label>owner<select name="assignee_id" defaultValue={r.assignee_id??''}><option value="">Unassigned</option>{reviewers.map(person=><option key={person.id} value={person.id}>{person.email} ({person.role.toLowerCase()})</option>)}</select></label>
        {(['component','regression_test','resolution'] as const).map(k=><label key={k}>{k.replace('_',' ')}<input name={k} defaultValue={r[k]} required={k==='component'} maxLength={k==='component'?120:k==='resolution'?2000:300}/></label>)}
        <button disabled={busy}>Save triage</button></form>:<button disabled={busy} onClick={()=>{if(confirm('Permanently delete this report?'))void perform(()=>request('/v1/feedback/'+r.id+'/delete',token,{method:'POST'}),'Deleted.')}}>Delete report</button>}
    </article>)}
    {!rows.length&&<p>No reports on this page.</p>}
    <button disabled={!offset} onClick={()=>page(n=>n-25)}>Previous</button><button disabled={rows.length<25} onClick={()=>page(n=>n+25)}>Next</button>
  </section>
}
