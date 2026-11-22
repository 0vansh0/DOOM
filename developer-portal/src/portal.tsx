import { FeedbackDesk } from './FeedbackDesk';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, request, requestBlob, uploadPackage } from './api';
import { humanBytes, statusTone, validateDoommodFile, type AuditEvent, type Profile, type QueueItem, type Submission, type User, type UserReport } from './types';

type Section = 'overview' | 'submissions' | 'upload' | 'account' | 'moderation' | 'reports' | 'audit' | 'feedback' | 'triage';
type AuthResponse = { accessToken: string; expiresIn: number; user: User };
type DraftResponse = { id: string; status: string };
type UploadResponse = { id: string; status: string; modId?: string | null; version?: string; sha256?: string };
const privileged = (role: User['role']) => role === 'REVIEWER' || role === 'ADMIN';
const date = (value?: string | null) => value ? new Date(value).toLocaleString() : '—';
const json = (value: unknown) => JSON.stringify(value ?? {}, null, 2);

export function Portal() {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [sessionLifetime, setSessionLifetime] = useState(900);
  const [section, setSection] = useState<Section>('overview');
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [reports, setReports] = useState<UserReport[]>([]);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [selected, setSelected] = useState<Submission | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  const logout = useCallback(() => { setToken(null); setUser(null); setSessionLifetime(900); setSelected(null); setSubmissions([]); setQueue([]); setReports([]); setAudit([]); setMessage('Signed out.'); }, []);

  useEffect(() => {
    if (!token || !user) return;
    const timer = window.setTimeout(logout, Math.max(60, sessionLifetime) * 1000);
    return () => window.clearTimeout(timer);
  }, [token, user, sessionLifetime, logout]);

  const refresh = useCallback(async () => {
    if (!token || !user) return;
    setLoading(true); setMessage(null);
    try {
      const own = await request<Submission[]>('/v1/submissions', token);
      setSubmissions(own);
      if (privileged(user.role)) {
        const [pending, openReports, events] = await Promise.all([
          request<QueueItem[]>('/v1/moderation/submissions', token),
          request<UserReport[]>('/v1/moderation/reports', token),
          request<AuditEvent[]>('/v1/moderation/audit?limit=50', token),
        ]);
        setQueue(pending); setReports(openReports); setAudit(events);
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) logout();
      else setMessage(error instanceof Error ? error.message : 'Could not load portal data.');
    } finally { setLoading(false); }
  }, [token, user, logout]);

  useEffect(() => { void refresh(); }, [refresh]);

  const openSubmission = async (id: string) => {
    if (!token) return;
    setBusy(true); setMessage(null);
    try { setSelected(await request<Submission>(`/v1/submissions/${encodeURIComponent(id)}`, token)); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Submission details are unavailable.'); }
    finally { setBusy(false); }
  };

  const act = async (operation: () => Promise<unknown>, success: string) => {
    setBusy(true); setMessage(null);
    try { await operation(); setMessage(success); await refresh(); if (selected) await openSubmission(selected.id); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'The request could not be completed.'); }
    finally { setBusy(false); }
  };

  if (!token || !user) return <Login onAuthenticated={(auth) => { setToken(auth.accessToken); setUser(auth.user); setSessionLifetime(auth.expiresIn); setSection('overview'); setMessage(null); }} message={message} />;

  const nav: Array<{ id: Section; label: string; group?: string }> = [
    { id: 'overview', label: 'Dashboard' }, { id: 'submissions', label: 'My submissions' }, { id: 'upload', label: 'New submission' }, { id: 'account', label: 'Developer profile' },
    { id: 'feedback' as const, label: 'Private feedback', group: 'Developer' },
    ...(privileged(user.role) ? [{ id: 'triage' as const, label: 'Issue triage', group: 'Moderation' }, { id: 'moderation' as const, label: 'Review queue', group: 'Moderation' }, { id: 'reports' as const, label: 'User reports' }, { id: 'audit' as const, label: 'Audit history' }] : []),
  ];

  return <div className="shell">
    <header className="topbar"><a className="brand" href="#dashboard" onClick={(e) => { e.preventDefault(); setSection('overview'); setSelected(null); }}><span className="brand-mark">D</span> DOOM <small>Developer Portal</small></a>
      <div className="account-chip"><span className="role-pill">{user.role}</span><span>{user.email}</span><button className="button quiet" onClick={logout}>Sign out</button></div></header>
    <div className="layout"><aside className="sidebar" aria-label="Portal navigation">
      <div className="workspace-label">WORKSPACE</div>{nav.map(item => <button key={item.id} className={`nav-item ${section === item.id ? 'active' : ''}`} onClick={() => { setSection(item.id); setSelected(null); setMessage(null); }}><span>{item.label}</span>{item.id === 'moderation' && queue.length > 0 && <b className="count">{queue.length}</b>}</button>)}
      <div className="sidebar-bottom"><span className="status-dot" /> Local marketplace API <button onClick={() => void refresh()} aria-label="Refresh portal data" className="icon-button">↻</button></div>
    </aside>
    <main className="main">
      <div className="page-head"><div><div className="eyebrow">{privileged(user.role) && ['moderation','reports','audit'].includes(section) ? 'TRUST & SAFETY' : 'DEVELOPER WORKSPACE'}</div><h1>{selected ? 'Submission review' : nav.find(item => item.id === section)?.label}</h1></div><div className="page-actions">{loading && <span className="muted">Refreshing…</span>}<button className="button secondary" onClick={() => void refresh()} disabled={loading}>Refresh</button></div></div>
      {message && <div className="notice" role="status">{message}<button aria-label="Dismiss message" onClick={() => setMessage(null)}>×</button></div>}
      {selected ? <SubmissionDetail submission={selected} user={user} token={token} busy={busy} onBack={() => setSelected(null)} onAction={act} onMessage={setMessage} /> : <>
        {section === 'overview' && <Dashboard submissions={submissions} queue={queue} reports={reports} loading={loading} onOpen={openSubmission} onNavigate={setSection} />}
        {section === 'submissions' && <SubmissionList submissions={submissions} search={search} setSearch={setSearch} onOpen={openSubmission} loading={loading} />}
        {section === 'feedback' && <FeedbackDesk key={user.id} token={token} review={false} />}
        {section === 'triage' && privileged(user.role) && <FeedbackDesk key={'review'+user.id} token={token} review />}
        {section === 'upload' && <UploadFlow token={token} submissions={submissions} busy={busy} setBusy={setBusy} onDone={async (id, text) => { setMessage(text); await refresh(); await openSubmission(id); }} />}
        {section === 'account' && <ProfileEditor token={token} user={user} onMessage={setMessage} />}
        {section === 'moderation' && privileged(user.role) && <ModerationQueue items={queue} search={search} setSearch={setSearch} onOpen={openSubmission} />}
        {section === 'reports' && privileged(user.role) && <Reports items={reports} token={token} busy={busy} onAct={act} />}
        {section === 'audit' && privileged(user.role) && <Audit events={audit} />}
      </>}
      <footer className="footer">Marketplace approval means a package passed review. It is not a safety guarantee. All package capabilities still require user authorization in DOOM.</footer>
    </main></div>
  </div>;
}

function Login({ onAuthenticated, message }: { onAuthenticated: (auth: AuthResponse) => void; message: string | null }) {
  const [register, setRegister] = useState(false); const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [displayName, setDisplayName] = useState(''); const [error, setError] = useState(message); const [busy, setBusy] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      const data = await request<AuthResponse>(register ? '/v1/auth/register' : '/v1/auth/login', undefined, { method: 'POST', body: JSON.stringify(register ? { email, password, displayName } : { email, password }) });
      onAuthenticated(data);
    } catch (e) { setError(e instanceof Error ? e.message : 'Authentication failed.'); }
    finally { setBusy(false); }
  };
  return <main className="auth-page"><div className="auth-card"><a className="brand" href="#home"><span className="brand-mark">D</span> DOOM <small>Developer Portal</small></a><div className="eyebrow">COMMUNITY RELEASES</div><h1>{register ? 'Create developer account' : 'Welcome back'}</h1><p className="muted">Manage package submissions and published releases.</p>
    {error && <div className="notice error" role="alert">{error}</div>}
    <form onSubmit={submit} className="form-stack">{register && <label>Developer display name<input required minLength={2} maxLength={80} value={displayName} onChange={e => setDisplayName(e.target.value)} autoComplete="nickname" /></label>}<label>Email address<input type="email" required maxLength={320} value={email} onChange={e => setEmail(e.target.value)} autoComplete="email" /></label><label>Password<input type="password" required minLength={register ? 12 : 1} maxLength={128} value={password} onChange={e => setPassword(e.target.value)} autoComplete={register ? 'new-password' : 'current-password'} /></label><button className="button primary full" disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button></form>
    <p className="auth-switch">{register ? 'Already have an account?' : 'New to DOOM?'} <button className="text-button" onClick={() => { setRegister(!register); setError(null); }}> {register ? 'Sign in' : 'Create an account'}</button></p><p className="security-note">Your access session stays in memory for this tab and expires after 15 minutes. Signing out clears it.</p></div></main>;
}

function Dashboard({ submissions, queue, reports, loading, onOpen, onNavigate }: { submissions: Submission[]; queue: QueueItem[]; reports: UserReport[]; loading: boolean; onOpen: (id: string) => void; onNavigate: (section: Section) => void }) {
  const pending = submissions.filter(s => ['DRAFT','UPLOADED','VALIDATING','PENDING_REVIEW','APPROVED'].includes(s.status));
  const published = submissions.filter(s => s.status === 'PUBLISHED');
  return <div className="content-stack"><section className="welcome card"><div><div className="eyebrow">RELEASE CONTROL</div><h2>Ship your next DOOM mod.</h2><p>Upload a declarative <code>.doommod</code>, review server validation, and submit each immutable version for moderation.</p><button className="button primary" onClick={() => onNavigate('upload')}>Start a submission <span>→</span></button></div><div className="welcome-art" aria-hidden="true"><span>SDK</span><i>v</i><b>+</b></div></section>
    <div className="stat-grid"><Stat label="My submissions" value={submissions.length} hint="Owned by your account"/><Stat label="Awaiting action" value={pending.length} hint="Draft, validation, or review"/><Stat label="Published versions" value={published.length} hint="Immutable releases"/>{(queue.length > 0 || reports.length > 0) && <Stat label="Review queue" value={queue.length + reports.length} hint="Submissions and reports"/>}</div>
    <section className="card"><div className="section-title"><div><h3>Recent submissions</h3><p>Current server-side status and validation summary.</p></div><button className="text-button" onClick={() => onNavigate('submissions')}>View all →</button></div>
      {loading && !submissions.length ? <LoadingRows/> : submissions.length === 0 ? <Empty title="No submissions yet" body="Create a draft or upload your first validated package." action="Upload a package" onClick={() => onNavigate('upload')} /> : <SubmissionTable rows={submissions.slice(0,5)} onOpen={onOpen}/>}
    </section>{(queue.length > 0 || reports.length > 0) && <section className="card moderation-callout"><div><h3>Moderation work is waiting</h3><p>{queue.length} pending submission{queue.length === 1 ? '' : 's'} · {reports.length} open report{reports.length === 1 ? '' : 's'}</p></div><button className="button secondary" onClick={() => onNavigate('moderation')}>Open review queue</button></section>}</div>;
}

function Stat({ label, value, hint }: { label: string; value: number; hint: string }) { return <div className="stat-card"><span>{label}</span><strong>{value}</strong><small>{hint}</small></div>; }
function LoadingRows() { return <div className="loading-lines" aria-label="Loading submissions"><i/><i/><i/></div>; }
function Empty({ title, body, action, onClick }: { title: string; body: string; action?: string; onClick?: () => void }) { return <div className="empty"><div className="empty-icon">◇</div><h3>{title}</h3><p>{body}</p>{action && onClick && <button className="button secondary" onClick={onClick}>{action}</button>}</div>; }

function SubmissionList({ submissions, search, setSearch, onOpen, loading }: { submissions: Submission[]; search: string; setSearch: (value: string) => void; onOpen: (id: string) => void; loading: boolean }) {
  const rows = submissions.filter(s => `${s.modId ?? ''} ${s.status} ${String(s.manifest?.name ?? '')}`.toLowerCase().includes(search.toLowerCase()));
  return <section className="card"><div className="toolbar"><label className="search">Search submissions<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Name, mod ID, or status" /></label><span className="muted">{rows.length} records · API limit 100</span></div>{loading && !rows.length ? <LoadingRows/> : rows.length ? <SubmissionTable rows={rows} onOpen={onOpen}/> : <Empty title="No matching submissions" body="Uploads, drafts, validation failures, and published versions appear here."/>}</section>;
}

function SubmissionTable({ rows, onOpen }: { rows: Submission[]; onOpen: (id: string) => void }) {
   return <div className="table-wrap"><table><thead><tr><th>Mod / version</th><th>Status</th><th>Checksum</th><th>Updated</th><th></th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td><strong>{String(row.manifest?.name ?? row.modId ?? 'Draft')}</strong><small>{row.modId ?? row.id}</small>{row.manifest?.version != null ? <small>v{String(row.manifest.version)}</small> : null}</td><td><span className={`status ${statusTone(row.status)}`}>{row.status.replaceAll('_',' ')}</span></td><td><code>{row.sha256?.slice(0,12) ?? '—'}</code></td><td>{date(row.updatedAt)}</td><td><button className="text-button" onClick={() => onOpen(row.id)}>Details →</button></td></tr>)}</tbody></table></div>;
}

function UploadFlow({ token, submissions, busy, setBusy, onDone }: { token: string; submissions: Submission[]; busy: boolean; setBusy: (busy: boolean) => void; onDone: (id: string, message: string) => void }) {
  const [file, setFile] = useState<File | null>(null); const [error, setError] = useState<string | null>(null); const [notice, setNotice] = useState<string | null>(null); const [progress, setProgress] = useState(0); const [draftId, setDraftId] = useState(''); const [modId, setModId] = useState(''); const [controller, setController] = useState<AbortController | null>(null);
  const drafts = submissions.filter(s => s.status === 'DRAFT');
  const createDraft = async () => { setError(null); setBusy(true); try { const draft = await request<DraftResponse>('/v1/submissions/drafts', token, { method:'POST', body: JSON.stringify(modId.trim() ? { modId: modId.trim() } : {}) }); setDraftId(draft.id); } catch(e) { setError(e instanceof Error ? e.message : 'Could not create draft.'); } finally { setBusy(false); } };
  const saveDraft = async () => { if (!draftId) return; setError(null); setNotice(null); setBusy(true); try { await request(`/v1/submissions/${encodeURIComponent(draftId)}/draft`, token, { method:'PATCH', body: JSON.stringify({ modId: modId.trim() || null }) }); setNotice('Draft saved.'); } catch(e) { setError(e instanceof Error ? e.message : 'Could not save draft.'); } finally { setBusy(false); } };
  const upload = async (event: React.FormEvent) => {
    event.preventDefault(); if (!file) return;
    const problem = validateDoommodFile(file); if (problem) { setError(problem); return; }
    const aborter = new AbortController(); setController(aborter); setProgress(0); setError(null); setBusy(true);
    try {
      const path = draftId ? `/v1/submissions/${encodeURIComponent(draftId)}/package` : '/v1/submissions';
      const result = await uploadPackage(path, token, file, setProgress, aborter.signal) as UploadResponse;
      onDone(result.id, `Package uploaded to private quarantine. Server status: ${result.status}.`);
      setFile(null); setDraftId('');
    } catch(e) { if (e instanceof DOMException && e.name === 'AbortError') setError('Upload cancelled.'); else setError(e instanceof Error ? e.message : 'Package upload failed.'); }
    finally { setController(null); setBusy(false); }
  };
  return <div className="upload-grid"><section className="card"><div className="eyebrow">NEW RELEASE</div><h2>Submit a .doommod package</h2><p className="muted">The backend validates the archive, stores it in private quarantine, and never publishes it automatically. Package manifest fields remain authoritative for name, ID, description, API level, definition, and capabilities.</p>
    {error && <div className="notice error" role="alert">{error}</div>}{notice && <div className="notice success" role="status">{notice}</div>}
    <form className="form-stack" onSubmit={upload}>
      <label>Target draft <select value={draftId} onChange={e => { const id=e.target.value; setDraftId(id); setModId(drafts.find(d=>d.id===id)?.modId ?? ''); setNotice(null); }}><option value="">Create a new submission from upload</option>{drafts.map(d => <option key={d.id} value={d.id}>{d.modId || 'Unassigned draft'} · {d.id.slice(0,8)}</option>)}</select></label>
      {draftId && <><p className="muted small">Draft {draftId}. Mod ID can be edited before attaching the package; subsequent package contents must match it.</p><div className="inline-form"><label>Mod ID<input value={modId} onChange={e=>setModId(e.target.value)} maxLength={64} placeholder="org.author.mod" /></label><button type="button" className="button secondary" disabled={busy} onClick={()=>void saveDraft()}>Save draft</button></div></>}
      {!draftId && <div className="inline-form"><label>Optional mod ID for a new draft<input value={modId} onChange={e => setModId(e.target.value)} maxLength={64} placeholder="org.author.mod" /></label><button type="button" className="button secondary" disabled={busy} onClick={() => void createDraft()}>Create draft</button></div>}
      <label className="file-drop"><span className="file-icon">↑</span><strong>{file ? file.name : 'Choose a package'}</strong><small>{file ? humanBytes(file.size) : '.doommod only · maximum 10 MB'}</small><input aria-label="Choose .doommod package" type="file" accept=".doommod,application/vnd.doom.mod" onChange={e => { const next = e.target.files?.[0] ?? null; const issue = next && validateDoommodFile(next); setError(issue ?? null); setFile(issue ? null : next); }} /></label>
      {busy && <div className="progress"><div style={{ width: `${progress}%` }}/><span>{progress}% · uploading into quarantine</span></div>}
      <div className="row"><button className="button primary" disabled={!file || busy}>{busy ? 'Uploading…' : 'Upload for validation'}</button>{busy && <button type="button" className="button secondary" onClick={() => controller?.abort()}>Cancel upload</button>}</div>
      <p className="small muted">Category is selected when publishing an approved submission. Current API does not store changelogs, screenshots, or external support links. Use your package description and developer profile; no unsupported fields are silently accepted.</p>
    </form></section><aside className="card checklist"><h3>Release flow</h3><ol><li><b>Upload</b><span>Server package validator checks archive and manifest.</span></li><li><b>Validate</b><span>Revalidation moves the package to pending review.</span></li><li><b>Moderate</b><span>Only a separate reviewer can approve or reject.</span></li><li><b>Publish</b><span>Owner publishes the approved immutable version.</span></li></ol></aside></div>;
}

function SubmissionDetail({ submission, user, token, busy, onBack, onAction, onMessage }: { submission: Submission; user: User; token: string; busy: boolean; onBack: () => void; onAction: (fn: () => Promise<unknown>, success: string) => Promise<void>; onMessage: (message: string) => void }) {
  const [category, setCategory] = useState('utilities'); const [reason, setReason] = useState(''); const [reviewBusy, setReviewBusy] = useState(false); const [moderationBusy, setModerationBusy] = useState(false);
  const manifest = submission.manifest ?? {}; const definition = submission.definition ?? {};
  const modId = typeof manifest.id === 'string' ? manifest.id : submission.modId;
  const version = typeof manifest.version === 'string' ? manifest.version : '';
  const capabilities = Array.isArray(manifest.capabilities) ? manifest.capabilities.filter((x): x is string => typeof x === 'string') : [];
  const validations = submission.validations ?? [];
  const downloadReviewPackage = async () => {
    setReviewBusy(true);
    try {
      const result = await requestBlob(`/v1/moderation/submissions/${submission.id}/package/download`, token);
      if (!submission.sha256 || result.sha256 !== submission.sha256) throw new Error('Review package checksum metadata did not match this submission.');
      const bytes = await result.blob.arrayBuffer();
      const actual = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), value => value.toString(16).padStart(2, '0')).join('');
      if (actual !== submission.sha256) throw new Error('Review package checksum verification failed.');
      const objectUrl = URL.createObjectURL(result.blob); const anchor = document.createElement('a');
      anchor.href = objectUrl; anchor.download = `${modId}-${version}.doommod`; anchor.click(); URL.revokeObjectURL(objectUrl);
      onMessage('Review package downloaded and SHA-256 verified.');
    } catch(e) { onMessage(e instanceof Error ? e.message : 'Could not download a verified review package.'); }
    finally { setReviewBusy(false); }
  };
  const decide = async (decision: 'APPROVE' | 'REJECT') => {
    if (reason.trim().length < 8) { onMessage('Enter a moderation reason of at least 8 characters.'); return; }
    if (!window.confirm(`${decision === 'APPROVE' ? 'Approve' : 'Reject'} this immutable package submission? The decision will be audited.`)) return;
    setModerationBusy(true);
    try { await onAction(() => request(`/v1/moderation/submissions/${submission.id}/decision`, token, { method:'POST', body: JSON.stringify({ decision, reason: reason.trim() }) }), `Submission ${decision === 'APPROVE' ? 'approved' : 'rejected'}.`); }
    catch(e) { onMessage(e instanceof Error ? e.message : 'Moderation action failed.'); }
    finally { setModerationBusy(false); }
  };
  const quarantine = async () => {
    if (user.role !== 'ADMIN' || !modId || !version) return;
    if (!reason.trim() || !window.confirm('Quarantine this published release? New downloads will be blocked.')) return;
    setModerationBusy(true);
    try { await onAction(() => request(`/v1/moderation/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/quarantine`, token, { method:'POST', body: JSON.stringify({ reason: reason.trim() }) }), 'Release quarantined.'); }
    catch(e) { onMessage(e instanceof Error ? e.message : 'Quarantine failed.'); }
    finally { setModerationBusy(false); }
  };
  return <div className="content-stack"><button className="text-button back" onClick={onBack}>← Back to list</button><div className="detail-grid"><div className="content-stack">
    <section className="card"><div className="section-title"><div><span className={`status ${statusTone(submission.status)}`}>{submission.status.replaceAll('_',' ')}</span><h2>{String(manifest.name ?? (submission.modId || 'Draft'))}</h2><p className="muted">{submission.modId ?? 'Mod identity not attached'} · {version ? `v${version}` : 'No package attached'}</p></div><span className="status neutral">{humanBytes(submission.byteSize)}</span></div><div className="facts"><Fact label="Manifest schema" value={String(manifest.schemaVersion ?? '—')}/><Fact label="Definition" value={String(definition.definitionVersion ?? '—')}/><Fact label="Minimum Android API" value={String(manifest.minAndroidApi ?? '—')}/><Fact label="Checksum" value={submission.sha256 ?? '—'}/><Fact label="Created" value={date(submission.createdAt)}/><Fact label="Updated" value={date(submission.updatedAt)}/></div>
      <h3>Package manifest</h3><pre className="json-view">{json(manifest)}</pre><h3>Requested capabilities</h3>{capabilities.length ? <div className="chips">{capabilities.map(cap => <span className="chip" key={cap}>{cap}</span>)}</div> : <p className="muted">No declared capabilities.</p>}
      <h3>Definition summary</h3><pre className="json-view">{json(definition)}</pre></section>
    <section className="card"><h3>Validation history</h3>{validations.length === 0 ? <p className="muted">No separate validation attempts are recorded.</p> : validations.map((item, i) => <div className="validation-row" key={`${item.createdAt}-${i}`}><span className={`status ${item.passed ? 'good' : 'bad'}`}>{item.passed ? 'PASSED' : 'FAILED'}</span><span>{item.validatorVersion}</span><time>{date(item.createdAt)}</time><pre className="json-view">{json(item.diagnostics)}</pre></div>)}<p className="small muted">Initial upload validation is summarized by the server. Package contents are untrusted and are never executed by this portal.</p></section>
    <section className="card"><h3>Moderation history</h3>{submission.moderationDecisions?.length ? submission.moderationDecisions.map((decision,i)=><p key={i}><span className={`status ${decision.decision === 'APPROVE' ? 'good' : 'bad'}`}>{decision.decision}</span> {decision.reason} <time>{date(decision.createdAt)}</time></p>) : <p className="muted">No moderation decision recorded.</p>}</section>
    </div><aside className="content-stack"><section className="card action-card"><h3>Next action</h3><p className="muted">Actions are checked by the backend for ownership, role, and legal state transitions.</p>
      {submission.status === 'DRAFT' && <button className="button primary full" onClick={() => onMessage('Choose this draft in New submission and attach a .doommod package.')}>Attach a package in New submission</button>}
      {submission.status === 'UPLOADED' && submission.ownerId === user.id && <button className="button primary full" disabled={busy} onClick={() => void onAction(() => request(`/v1/submissions/${submission.id}/validate`, token, { method:'POST' }), 'Server validation complete; submission is queued for review.')}>Validate and request review</button>}
      {submission.status === 'PENDING_REVIEW' && privileged(user.role) && <><button className="button secondary full" disabled={reviewBusy} onClick={() => void downloadReviewPackage()}>{reviewBusy ? 'Verifying package…' : 'Download and verify package'}</button><label>Moderation reason<textarea value={reason} onChange={e=>setReason(e.target.value)} minLength={8} maxLength={1000} rows={3} placeholder="Explain the decision (8–1000 characters)"/></label><button className="button primary full" disabled={moderationBusy} onClick={()=>void decide('APPROVE')}>Approve submission</button><button className="button danger full" disabled={moderationBusy} onClick={()=>void decide('REJECT')}>Reject with reason</button></>}
      {submission.status === 'APPROVED' && submission.ownerId === user.id && <><label>Catalog category<select value={category} onChange={e=>setCategory(e.target.value)}><option value="utilities">Utilities</option><option value="appearance">Appearance</option><option value="accessibility">Accessibility</option><option value="other">Other</option></select></label><button className="button primary full" disabled={busy} onClick={()=>void onAction(()=>request(`/v1/submissions/${submission.id}/publish`,token,{method:'POST',body:JSON.stringify({category})}),'Version published. The release artifact is immutable.')}>Publish approved version</button></>}
      {submission.status === 'PUBLISHED' && <div className="notice success">Published releases are immutable. Upload a new package version to create a separately reviewed submission.</div>}
      {user.role === 'ADMIN' && submission.status === 'PUBLISHED' && <><label>Quarantine reason<textarea value={reason} onChange={e=>setReason(e.target.value)} maxLength={1000} rows={3}/></label><button className="button danger full" disabled={moderationBusy} onClick={()=>void quarantine()}>Quarantine this release</button></>}
      {['REJECTED','VALIDATION_FAILED','QUARANTINED'].includes(submission.status) && <div className="notice error">Review the recorded validation or moderation reason below. Create a new package submission for corrections; published versions cannot be overwritten.</div>}
    </section>{submission.ownerId && <div className="card"><h3>Ownership</h3><p className="small muted">Account ID: <code>{submission.ownerId}</code></p><p className="small muted">The API only returns this detail to the owner and authorized reviewers.</p></div>}</aside></div></div>;
}
function Fact({ label, value }: { label: string; value: string }) { return <div><small>{label}</small><strong>{value}</strong></div>; }

function ModerationQueue({ items, search, setSearch, onOpen }: { items: QueueItem[]; search: string; setSearch: (value:string)=>void; onOpen:(id:string)=>void }) {
  const filtered = items.filter(row => `${row.modId ?? ''} ${row.name ?? ''} ${row.version ?? ''} ${row.ownerId}`.toLowerCase().includes(search.toLowerCase()));
  return <section className="card"><div className="toolbar"><label className="search">Search pending submissions<input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Name, mod ID, account ID"/></label><span className="muted">{filtered.length} pending</span></div>{filtered.length ? <div className="table-wrap"><table><thead><tr><th>Package</th><th>Developer account</th><th>Submitted</th><th></th></tr></thead><tbody>{filtered.map(item=><tr key={item.id}><td><strong>{item.name ?? item.modId ?? 'Package review'}</strong><small>{item.modId} · {item.version}</small></td><td><code>{item.ownerId}</code></td><td>{date(item.createdAt)}</td><td><button className="text-button" onClick={()=>onOpen(item.id)}>Inspect →</button></td></tr>)}</tbody></table></div> : <Empty title="No pending submissions" body="Only packages in PENDING_REVIEW are shown here."/>}</section>;
}

function Reports({ items, token, busy, onAct }: { items: UserReport[]; token: string; busy: boolean; onAct: (fn:()=>Promise<unknown>, success:string)=>Promise<void> }) {
  const [reason, setReason] = useState<Record<string,string>>({});
  return <div className="content-stack">{items.length ? items.map(report=><section className="card report-card" key={report.id}><div className="section-title"><div><span className="status warn">OPEN</span><h3>{report.modId} {report.version && `· ${report.version}`}</h3></div><time>{date(report.createdAt)}</time></div><p>{report.reason}</p><small className="muted">Report ID {report.id} · reporter {report.reporterId ?? 'deleted account'}</small><label>Resolution note<textarea value={reason[report.id] ?? ''} onChange={e=>setReason({...reason,[report.id]:e.target.value})} maxLength={1000} minLength={8} rows={2} placeholder="Resolution note (minimum 8 characters)"/></label><div className="row"><button className="button primary" disabled={busy} onClick={()=>{ const text=reason[report.id]?.trim() ?? ''; if(text.length<8)return; if(window.confirm('Mark this report as actioned?')) void onAct(()=>request(`/v1/moderation/reports/${report.id}/decision`,token,{method:'POST',body:JSON.stringify({decision:'ACTIONED',reason:text})}),'Report action recorded.'); }}>Mark actioned</button><button className="button secondary" disabled={busy} onClick={()=>{ const text=reason[report.id]?.trim() ?? ''; if(text.length<8)return; if(window.confirm('Dismiss this report?')) void onAct(()=>request(`/v1/moderation/reports/${report.id}/decision`,token,{method:'POST',body:JSON.stringify({decision:'DISMISSED',reason:text})}),'Report dismissed.'); }}>Dismiss report</button></div></section>) : <section className="card"><Empty title="No open reports" body="User report records will appear here when submitted through the catalog API."/></section>}</div>;
}

function Audit({ events }: { events: AuditEvent[] }) { return <section className="card"><h3>Recent audit history</h3><p className="muted">Only reviewed, non-report-content triage changes are shown in event details.</p>{events.length ? <div className="table-wrap"><table><thead><tr><th>Action</th><th>Subject</th><th>Actor</th><th>Time</th><th>Change</th></tr></thead><tbody>{events.map(event=><tr key={event.id}><td><strong>{event.action}</strong></td><td>{event.subjectType} · {event.subjectId ?? '—'}</td><td>{event.actorId ?? 'system'}</td><td>{date(event.createdAt)}</td><td>{event.details ? `Status ${String(event.details.previousStatus)} → ${String(event.details.newStatus)} · Priority ${String(event.details.previousPriority)} → ${String(event.details.newPriority)}` : '—'}</td></tr>)}</tbody></table></div> : <Empty title="No audit events" body="No events were returned by the API."/>}</section>; }

function ProfileEditor({ token, user, onMessage }: { token: string; user: User; onMessage:(message:string)=>void }) {
  const [profile,setProfile]=useState<Profile|null>(null);const [displayName,setDisplayName]=useState('');const [bio,setBio]=useState('');const [website,setWebsite]=useState('');const [busy,setBusy]=useState(false);const [error,setError]=useState<string|null>(null);
  useEffect(()=>{let alive=true;void request<Profile>(`/v1/catalog/developers/${encodeURIComponent(user.id)}`).then(value=>{if(alive){setProfile(value);setDisplayName(value.displayName);setBio(value.bio ?? '');setWebsite(value.website ?? '');}}).catch(e=>{if(alive)setError(e instanceof Error?e.message:'Profile could not be loaded.');});return()=>{alive=false;};},[token,user.id]);
  const submit=async(e:React.FormEvent)=>{e.preventDefault();setBusy(true);setError(null);try{const value=await request<Profile>('/v1/developer/profile',token,{method:'PATCH',body:JSON.stringify({displayName,bio,website:website.trim()||null})});setProfile(value);onMessage('Developer profile saved.');}catch(e){setError(e instanceof Error?e.message:'Profile update failed.');}finally{setBusy(false);}};
  return <section className="card profile-card"><div className="eyebrow">PUBLIC DEVELOPER PROFILE</div><h2>Account settings</h2><p className="muted">Email and role are managed by the authenticated account. Role is never editable here.</p>{error&&<div className="notice error">{error}</div>}<form onSubmit={submit} className="form-stack"><label>Display name<input required minLength={2} maxLength={80} value={displayName} onChange={e=>setDisplayName(e.target.value)}/></label><label>Bio<textarea maxLength={500} rows={4} value={bio} onChange={e=>setBio(e.target.value)}/><small>{bio.length}/500</small></label><label>Website<input type="url" maxLength={2048} value={website} onChange={e=>setWebsite(e.target.value)} placeholder="https://example.com"/></label><div className="facts"><Fact label="Email" value={user.email}/><Fact label="Role" value={user.role}/><Fact label="Profile loaded" value={profile?'Yes':'Loading'}/></div><button className="button primary" disabled={busy||!profile}>{busy?'Saving…':'Save profile'}</button></form></section>;
}
