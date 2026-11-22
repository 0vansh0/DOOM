import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { buildServer } from '../src/server.js';
import { MemoryArtifactStorage, S3ArtifactStorage } from '../src/storage/s3.js';
import type { ArtifactStorage } from '../src/storage/s3.js';
import { db, client } from '../src/db/index.js';
import { auditEvents, betaFeedback, developerProfiles, modVersions, moderationDecisions, mods, submissions, userReports, users, validationResults } from '../src/db/schema.js';
import { pruneExpiredFeedback } from '../src/feedback.js';
import { hash, Algorithm } from '@node-rs/argon2';
import { eq } from 'drizzle-orm';
import { createHash } from 'node:crypto';
import type { FastifyInstance } from 'fastify';

const integration = process.env.MARKETPLACE_INTEGRATION === '1';
describe.skipIf(!integration)('marketplace API integration',()=>{
  const memoryStorage=new MemoryArtifactStorage();
  const storage:ArtifactStorage=process.env.MARKETPLACE_S3_INTEGRATION==='1'?new S3ArtifactStorage():memoryStorage;
  let app:FastifyInstance;
  let origin='';let ownerToken='';let reviewerToken='';let adminToken='';let ownerId='';let reviewerId='';
  const packageBytes=async()=>readFile(process.env.MARKETPLACE_PACKAGE_UNDER_TEST ?? resolve(process.cwd(),'..','sample-mods','community','low-battery-alert.doommod'));
  async function call(path:string,init:RequestInit={}) { return fetch(origin+path,init); }
  beforeAll(async()=>{
    app=await buildServer({storage});
    await db.delete(betaFeedback);await db.delete(userReports);await db.delete(moderationDecisions);await db.delete(validationResults);await db.delete(submissions);await db.delete(modVersions);await db.delete(mods);await db.delete(auditEvents);await db.delete(developerProfiles);await db.delete(users);
    await app.listen({host:'127.0.0.1',port:0});
    const address=app.server.address();if(!address||typeof address==='string')throw new Error('Integration server did not bind');origin=`http://127.0.0.1:${address.port}`;
    const owner=await call('/v1/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'owner@example.test',password:'test-owner-password-42',displayName:'Package Owner'})});
    expect(owner.status).toBe(201);const ownerData=await owner.json() as {data:{accessToken:string;user:{id:string}}};ownerToken=ownerData.data.accessToken;ownerId=ownerData.data.user.id;
    const reviewerPasswordHash=await hash('test-reviewer-password-42',{algorithm:Algorithm.Argon2id,memoryCost:19456,timeCost:2,parallelism:1});
    const [reviewer]=await db.insert(users).values({email:'reviewer@example.test',passwordHash:reviewerPasswordHash,role:'REVIEWER'}).returning({id:users.id});reviewerId=reviewer!.id;
    await db.insert(developerProfiles).values({userId:reviewerId,displayName:'Test Reviewer'});
    const login=await call('/v1/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'reviewer@example.test',password:'test-reviewer-password-42'})});
    expect(login.status).toBe(200);reviewerToken=((await login.json()) as {data:{accessToken:string}}).data.accessToken;
    const adminPasswordHash=await hash('test-admin-password-42',{algorithm:Algorithm.Argon2id,memoryCost:19456,timeCost:2,parallelism:1});
    const [admin]=await db.insert(users).values({email:'admin@example.test',passwordHash:adminPasswordHash,role:'ADMIN'}).returning({id:users.id});
    await db.insert(developerProfiles).values({userId:admin!.id,displayName:'Test Administrator'});
    const adminLogin=await call('/v1/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'admin@example.test',password:'test-admin-password-42'})});
    expect(adminLogin.status).toBe(200);adminToken=((await adminLogin.json()) as {data:{accessToken:string}}).data.accessToken;
  });
  afterAll(async()=>{await app.close();await client.end();});

  it('requires explicit feedback consent and enforces private ownership and reviewer access',async()=>{
    const report={category:'Bug',title:'Fixture',description:'Test report',steps:'',expected:'',actual:'',appVersion:'test',androidVersion:'test',modId:''};
    const post=(path:string,token:string,body:unknown)=>call(path,{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+token},body:JSON.stringify(body)});
    expect((await call('/v1/feedback')).status).toBe(401);
    expect((await post('/v1/feedback',ownerToken,{report,consent:false})).status).toBe(400);
    expect((await post('/v1/feedback',ownerToken,{report:{...report,extra:'private'},consent:true})).status).toBe(400);
    const send=async()=>{const res=await post('/v1/feedback',ownerToken,{report,consent:true});expect(res.status).toBe(201);return (await res.json() as {data:{id:string}}).data.id};
    const id=await send();expect(await send()).toBe(id);
    const ownerList=(await (await call('/v1/feedback',{headers:{authorization:'Bearer '+ownerToken}})).json() as {data:{id:string}[]}).data;
    expect(ownerList.map(row=>row.id)).toContain(id);
    expect((await call('/v1/feedback/'+id,{headers:{authorization:'Bearer '+ownerToken}})).status).toBe(200);
    expect((await call('/v1/feedback/'+id,{headers:{authorization:'Bearer '+reviewerToken}})).status).toBe(404);
    expect((await call('/v1/moderation/feedback/'+id,{headers:{authorization:'Bearer '+ownerToken}})).status).toBe(403);
    expect((await call('/v1/moderation/feedback',{headers:{authorization:'Bearer '+ownerToken}})).status).toBe(403);
    expect((await call('/v1/moderation/reviewers',{headers:{authorization:'Bearer '+ownerToken}})).status).toBe(403);
    expect((await (await call('/v1/feedback',{headers:{authorization:'Bearer '+reviewerToken}})).json() as {data:unknown[]}).data).toEqual([]);
    expect((await call('/v1/moderation/feedback/'+id,{headers:{authorization:'Bearer '+reviewerToken}})).status).toBe(200);
    expect((await call('/v1/moderation/feedback/'+id,{headers:{authorization:'Bearer '+adminToken}})).status).toBe(200);
    const reviewerList=await call('/v1/moderation/reviewers',{headers:{authorization:'Bearer '+reviewerToken}});
    expect(reviewerList.status).toBe(200);
    expect((await reviewerList.json() as {data:{id:string;email:string}[]}).data.map(user=>user.id)).toContain(reviewerId);
    expect((await call('/v1/feedback',{headers:{authorization:'Bearer not-a-jwt'}})).status).toBe(401);
    const expired=app.jwt.sign({sub:ownerId,exp:Math.floor(Date.now()/1000)-1});
    expect((await call('/v1/feedback',{headers:{authorization:'Bearer '+expired}})).status).toBe(401);
    const update={status:'resolved',priority:'P3',component:'test',reproduction:'reproduced',regression_test:'integration',resolution:'fixture',assignee_id:reviewerId};
    expect((await post('/v1/moderation/feedback/'+id,ownerToken,update)).status).toBe(403);
    expect((await post('/v1/moderation/feedback/'+id,reviewerToken,{...update,status:'closed'})).status).toBe(400);
    const triaged=await post('/v1/moderation/feedback/'+id,reviewerToken,update);
    expect(triaged.status).toBe(200);
    const triageData=(await triaged.json() as {data:{status:string;priority:string;assignee_id:string;updated_at:string}}).data;
    expect(triageData.status).toBe('resolved');expect(triageData.priority).toBe('P3');expect(triageData.assignee_id).toBe(reviewerId);expect(triageData.updated_at).toBeTruthy();
    expect((await post('/v1/moderation/feedback/'+id,reviewerToken,{...update,assignee_id:ownerId})).status).toBe(400);
    expect((await post('/v1/moderation/feedback/'+id,reviewerToken,{...update,status:'in-progress'})).status).toBe(409);
    const auditRows=await db.select().from(auditEvents).where(eq(auditEvents.subjectId,id));
    expect(auditRows).toHaveLength(1);expect(auditRows[0]!.action).toBe('FEEDBACK_TRIAGE_UPDATED');
    expect(auditRows[0]!.details).toMatchObject({previousStatus:'open',newStatus:'resolved',previousPriority:'P2',newPriority:'P3'});
    expect((await post('/v1/moderation/feedback/'+id,adminToken,{...update,status:'open',priority:'P1'})).status).toBe(200);
    expect(await db.select().from(auditEvents).where(eq(auditEvents.subjectId,id))).toHaveLength(2);
    expect((await post('/v1/feedback/'+id+'/delete',reviewerToken,{})).status).toBe(404);
    expect((await post('/v1/feedback/'+id+'/delete',ownerToken,{})).status).toBe(200);
    expect(await db.select().from(auditEvents).where(eq(auditEvents.subjectId,id))).toHaveLength(0);
  });
  it('prunes expired feedback and its linked audit history after 30 days',async()=>{
    const now=new Date();const oldCreatedAt=new Date(now.getTime()-31*24*60*60*1000);
    const [old]=await client`insert into beta_feedback(owner_id,digest,report,created_at) values(${ownerId},'expired-fixture',${JSON.stringify({title:'Expired fixture'})}::jsonb,${oldCreatedAt.toISOString()}::timestamptz) returning id`;
    await db.insert(auditEvents).values({actorId:reviewerId,action:'FEEDBACK_TRIAGE_UPDATED',subjectType:'feedback',subjectId:old!.id,details:{previousStatus:'open',newStatus:'resolved'}});
    await pruneExpiredFeedback(new Date(now.getTime()-30*24*60*60*1000));
    expect(await client`select id from beta_feedback where id=${old!.id}`).toHaveLength(0);
    expect(await db.select().from(auditEvents).where(eq(auditEvents.subjectId,old!.id))).toHaveLength(0);
  });
  it('enforces the per-IP feedback rate limit',async()=>{
    const report={category:'Bug',title:'Rate fixture',description:'Rate test',steps:'',expected:'',actual:'',appVersion:'test',androidVersion:'test',modId:''};
    const responses=await Promise.all(Array.from({length:15},(_,index)=>call('/v1/feedback',{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+ownerToken},body:JSON.stringify({consent:true,report:{...report,title:'Rate fixture '+index}})})));
    expect(responses.some(response=>response.status===429)).toBe(true);
  });
  it('validates, reviews, publishes immutably and exposes exact checksum through public catalog',async()=>{
    const bytes=await packageBytes();const form=new FormData();form.set('package',new Blob([bytes]),'unsafe-name.zip');
    const {validatePackage}=await import('../src/validation/worker.js');
    const expectedPackage=await validatePackage(bytes);
    const uploaded=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(uploaded.status).toBe(201);const uploadData=await uploaded.json() as {data:{id:string;sha256:string;status:string}};
    expect(uploadData.data.status).toBe('UPLOADED');
    const validate=await call(`/v1/submissions/${uploadData.data.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}});
    expect(validate.status).toBe(200);expect((await validate.json() as {data:{status:string}}).data.status).toBe('PENDING_REVIEW');
    const publicBefore=await call('/v1/catalog/mods');expect(publicBefore.status).toBe(200);expect((await publicBefore.json() as {data:unknown[]}).data).toHaveLength(0);
    await db.update(users).set({role:'REVIEWER'}).where(eq(users.id,ownerId));
    const selfReview=await call(`/v1/moderation/submissions/${uploadData.data.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'test approval'})});expect(selfReview.status).toBe(403);
    await db.update(users).set({role:'DEVELOPER'}).where(eq(users.id,ownerId));
    const decision=await call(`/v1/moderation/submissions/${uploadData.data.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'Passes automated package review.'})});expect(decision.status).toBe(200);
    const publish=await call(`/v1/submissions/${uploadData.data.id}/publish`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({category:'utilities'})});expect(publish.status).toBe(201);
    const search=encodeURIComponent(String(expectedPackage.manifest.name).split(/\s+/)[0] ?? '');
    const catalog=await call(`/v1/catalog/mods?search=${search}&capability=battery.read`);expect(catalog.status).toBe(200);const items=(await catalog.json() as {data:{id:string;sha256:string;version:string}[]}).data;
    const modId=String(expectedPackage.manifest.id),version=String(expectedPackage.manifest.version);
    expect(items).toHaveLength(1);expect(items[0]!.id).toBe(modId);expect(items[0]!.sha256).toBe(uploadData.data.sha256);
    const download=await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/download`);expect(download.status).toBe(200);
    const downloadData=(await download.json() as {data:{sha256:string;downloadUrl:string}}).data;expect(downloadData.sha256).toBe(uploadData.data.sha256);
    expect(downloadData.downloadUrl).toBe(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/package`);
    const packageResponse=await call(downloadData.downloadUrl);expect(packageResponse.status).toBe(200);
    const downloaded=Buffer.from(await packageResponse.arrayBuffer());
    expect(downloaded).toEqual(bytes);
    const downloadedSha256=createHash('sha256').update(downloaded).digest('hex');
    expect(downloadedSha256).toBe(uploadData.data.sha256);
    expect(downloadedSha256).toBe(downloadData.sha256);
    const importerResult=await validatePackage(downloaded);expect(importerResult.manifest.id).toBe(modId);
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/9.0.0/download`)).status).toBe(404);
    expect((await db.select().from(auditEvents)).length).toBeGreaterThanOrEqual(5);
  });
  it('records moderator rejection reasons and keeps rejected packages out of the public catalog',async()=>{
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','sdk2','low-battery-card.doommod'));
    const info=await (await import('../src/validation/worker.js')).validatePackage(bytes);
    const modId=String(info.manifest.id),version=String(info.manifest.version);
    const form=new FormData();form.set('package',new Blob([bytes]),'rejected-test-package.doommod');
    const upload=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(upload.status).toBe(201);const submission=(await upload.json() as {data:{id:string;status:string}}).data;
    expect(submission.status).toBe('UPLOADED');
    const validation=await call(`/v1/submissions/${submission.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}});
    expect(validation.status).toBe(200);expect((await validation.json() as {data:{status:string}}).data.status).toBe('PENDING_REVIEW');
    const rejectionReason='Rejected by isolated integration test for moderation verification.';
    const rejection=await call(`/v1/moderation/submissions/${submission.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'REJECT',reason:rejectionReason})});
    expect(rejection.status).toBe(200);expect((await rejection.json() as {data:{status:string}}).data.status).toBe('REJECTED');
    const details=await call(`/v1/submissions/${submission.id}`,{headers:{authorization:`Bearer ${ownerToken}`}});
    expect(details.status).toBe(200);const detailsData=(await details.json() as {data:{status:string;moderationDecisions:Array<{decision:string;reason:string}>}}).data;
    expect(detailsData.status).toBe('REJECTED');expect(detailsData.moderationDecisions).toContainEqual(expect.objectContaining({decision:'REJECT',reason:rejectionReason}));
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}`)).status).toBe(404);
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/download`)).status).toBe(404);
  });
  it('rejects a malformed package upload and records validation failure without storing it',async()=>{
    const form=new FormData();form.set('package',new Blob([Buffer.from('not a zip archive')]),'looks-valid.doommod');
    const response=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(response.status).toBe(422);const body=(await response.json()) as {data:{submissionId:string;status:string};error:{code:string}};
    expect(body.error.code).toBe('PACKAGE_INVALID');expect(body.data.status).toBe('VALIDATION_FAILED');
    const persisted=await db.select({status:submissions.status,quarantineKey:submissions.quarantineKey}).from(submissions).where(eq(submissions.id,body.data.submissionId));
    expect(persisted).toHaveLength(1);expect(persisted[0]!.status).toBe('VALIDATION_FAILED');expect(persisted[0]!.quarantineKey).toBeNull();
  });
  it('rejects unauthorized upload and cross-user submission access',async()=>{
    const bytes=await packageBytes();const form=new FormData();form.set('package',new Blob([bytes]),'x.doommod');
    expect((await call('/v1/submissions',{method:'POST',body:form})).status).toBe(401);
    const ownerSub=await db.select({id:submissions.id}).from(submissions).where(eq(submissions.ownerId,ownerId)).limit(1);
    const otherDeveloper=await call('/v1/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'other-developer@example.test',password:'test-other-developer-42',displayName:'Other Developer'})});
    expect(otherDeveloper.status).toBe(201);
    const otherToken=((await otherDeveloper.json()) as {data:{accessToken:string}}).data.accessToken;
    expect((await call(`/v1/submissions/${ownerSub[0]!.id}`,{headers:{authorization:`Bearer ${otherToken}`}})).status).toBe(404);
  });
  it('attaches a valid package to an owned draft, queues it for review, updates profile, and limits audit access',async()=>{
    const created=await call('/v1/submissions/drafts',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({modId:'local.powercard'})});
    expect(created.status).toBe(201);const draft=(await created.json() as {data:{id:string;status:string}}).data;expect(draft.status).toBe('DRAFT');
    expect((await call(`/v1/submissions/${draft.id}/draft`,{method:'PATCH',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({modId:'local.powercard'})})).status).toBe(200);
    expect((await call(`/v1/submissions/${draft.id}/draft`,{method:'PATCH',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({modId:'bad id'})})).status).toBe(400);
    const outsider=await call('/v1/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'draft-outsider@example.test',password:'test-outsider-password-42',displayName:'Draft Outsider'})});
    expect(outsider.status).toBe(201);const outsiderToken=((await outsider.json()) as {data:{accessToken:string}}).data.accessToken;
    expect((await call(`/v1/submissions/${draft.id}/draft`,{method:'PATCH',headers:{authorization:`Bearer ${outsiderToken}`,'content-type':'application/json'},body:JSON.stringify({modId:'local.other'})})).status).toBe(404);
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','sdk2','power-card.doommod'));
    const form=new FormData();form.set('package',new Blob([bytes]),'power-card.doommod');
    const attached=await call(`/v1/submissions/${draft.id}/package`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(attached.status).toBe(200);expect((await attached.json() as {data:{status:string;modId:string;version:string}}).data).toMatchObject({status:'UPLOADED',modId:'local.powercard',version:'1.0.0'});
    const queued=await call(`/v1/submissions/${draft.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}});
    expect(queued.status).toBe(200);expect((await queued.json() as {data:{status:string}}).data.status).toBe('PENDING_REVIEW');
    const reviewerDownload=await call(`/v1/moderation/submissions/${draft.id}/package/download`,{headers:{authorization:`Bearer ${reviewerToken}`}});
    expect(reviewerDownload.status).toBe(200);const reviewedBytes=Buffer.from(await reviewerDownload.arrayBuffer());
    expect(createHash('sha256').update(reviewedBytes).digest('hex')).toBe(reviewerDownload.headers.get('x-doom-sha256'));
    expect((await call(`/v1/moderation/submissions/${draft.id}/package/download`,{headers:{authorization:`Bearer ${ownerToken}`}})).status).toBe(403);
    const profile=await call('/v1/developer/profile',{method:'PATCH',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({displayName:'Package Owner Updated',bio:'SDK sample author',website:'https://example.test'})});
    expect(profile.status).toBe(200);expect((await profile.json() as {data:{displayName:string}}).data.displayName).toBe('Package Owner Updated');
    expect((await call('/v1/moderation/audit',{headers:{authorization:`Bearer ${ownerToken}`}})).status).toBe(403);
    const events=await call('/v1/moderation/audit?limit=5',{headers:{authorization:`Bearer ${reviewerToken}`}});
    expect(events.status).toBe(200);expect((await events.json() as {data:Array<{action:string}>}).data.some(event=>event.action==='DEVELOPER_PROFILE_UPDATED')).toBe(true);
  });
  it('allows only one moderation decision when reviewers race',async()=>{
    const bytes=await readFile(resolve(process.cwd(),'..','sample-mods','sdk2','charging-workflow.doommod'));
    const form=new FormData();form.set('package',new Blob([bytes]),'moderation-race.doommod');
    const upload=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(upload.status).toBe(201);const uploaded=(await upload.json() as {data:{id:string}}).data;
    const validated=await call(`/v1/submissions/${uploaded.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}});
    expect(validated.status).toBe(200);
    const decisions=await Promise.all([
      call(`/v1/moderation/submissions/${uploaded.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'Reviewed concurrent approval test.'})}),
      call(`/v1/moderation/submissions/${uploaded.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${adminToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'REJECT',reason:'Reviewed concurrent rejection test.'})})
    ]);
    expect(decisions.map(response=>response.status).sort()).toEqual([200,409]);
    const [record]=await db.select({status:submissions.status}).from(submissions).where(eq(submissions.id,uploaded.id));
    const recorded=await db.select().from(moderationDecisions).where(eq(moderationDecisions.submissionId,uploaded.id));
    expect(['APPROVED','REJECTED']).toContain(record!.status);
    expect(recorded).toHaveLength(1);
  });
  it.skipIf(!process.env.MARKETPLACE_UPDATE_PACKAGE_UNDER_TEST)('requires fresh review before publishing an immutable package update',async()=>{
    const bytes=await readFile(process.env.MARKETPLACE_UPDATE_PACKAGE_UNDER_TEST!);
    const {validatePackage}=await import('../src/validation/worker.js');
    const updateInfo=await validatePackage(bytes);
    const modId=String(updateInfo.manifest.id),version=String(updateInfo.manifest.version);
    const baseBytes=await packageBytes();
    const baseInfo=await validatePackage(baseBytes);
    const baseVersion=String(baseInfo.manifest.version);
    if(String(baseInfo.manifest.id)===modId&&baseVersion!==version){
      const prior=await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(baseVersion)}/package`);
      if(prior.status===404){
        const baseForm=new FormData();baseForm.set('package',new Blob([baseBytes]),'base.doommod');
        const baseUpload=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:baseForm});
        expect(baseUpload.status).toBe(201);const baseData=(await baseUpload.json() as {data:{id:string}}).data;
        expect((await call(`/v1/submissions/${baseData.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}})).status).toBe(200);
        expect((await call(`/v1/moderation/submissions/${baseData.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'Seed the existing release for update integration.'})})).status).toBe(200);
        expect((await call(`/v1/submissions/${baseData.id}/publish`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({category:'utilities'})})).status).toBe(201);
      }else expect(prior.status).toBe(200);
      expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(baseVersion)}/package`)).status).toBe(200);
    }
    const form=new FormData();form.set('package',new Blob([bytes]),'update.doommod');
    const uploaded=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:form});
    expect(uploaded.status).toBe(201);
    const uploadedData=(await uploaded.json() as {data:{id:string;sha256:string;status:string}}).data;
    expect(uploadedData.status).toBe('UPLOADED');
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/download`)).status).toBe(404);
    const validation=await call(`/v1/submissions/${uploadedData.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}});
    expect(validation.status).toBe(200);expect((await validation.json() as {data:{status:string}}).data.status).toBe('PENDING_REVIEW');
    const decision=await call(`/v1/moderation/submissions/${uploadedData.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'Reviewed compatible package update.'})});
    expect(decision.status).toBe(200);
    const publish=await call(`/v1/submissions/${uploadedData.id}/publish`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({category:'utilities'})});
    expect(publish.status).toBe(201);
    const downloaded=await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/package`);
    expect(downloaded.status).toBe(200);
    const packageBytesDownloaded=Buffer.from(await downloaded.arrayBuffer());
    expect(createHash('sha256').update(packageBytesDownloaded).digest('hex')).toBe(uploadedData.sha256);
    const sameVersionBytes=await readFile(resolve(process.cwd(),'..','sample-mods','community-examples','releases','battery-glow.doommod'));
    const duplicateForm=new FormData();duplicateForm.set('package',new Blob([sameVersionBytes]),'same-version-different-bytes.doommod');
    const duplicateUpload=await call('/v1/submissions',{method:'POST',headers:{authorization:`Bearer ${ownerToken}`},body:duplicateForm});
    expect(duplicateUpload.status).toBe(201);const duplicateData=(await duplicateUpload.json() as {data:{id:string}}).data;
    expect((await call(`/v1/submissions/${duplicateData.id}/validate`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`}})).status).toBe(200);
    expect((await call(`/v1/moderation/submissions/${duplicateData.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'APPROVE',reason:'Approved only to verify version immutability.'})})).status).toBe(200);
    const duplicatePublish=await call(`/v1/submissions/${duplicateData.id}/publish`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({category:'utilities'})});
    expect(duplicatePublish.status).toBe(409);expect((await duplicatePublish.json() as {error:{code:string}}).error.code).toBe('VERSION_EXISTS');
  });
  it('records and resolves user reports through moderator-only routes',async()=>{
    const {validatePackage}=await import('../src/validation/worker.js');const info=await validatePackage(await packageBytes());const modId=String(info.manifest.id);
    const report=await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/reports`,{method:'POST',headers:{authorization:`Bearer ${ownerToken}`,'content-type':'application/json'},body:JSON.stringify({version:String(info.manifest.version),reason:'Controlled integration report for moderation workflow.'})});
    expect(report.status).toBe(201);const reportData=(await report.json() as {data:{id:string;status:string}}).data;expect(reportData.status).toBe('OPEN');
    expect((await call('/v1/moderation/reports',{headers:{authorization:`Bearer ${ownerToken}`}})).status).toBe(403);
    const queue=await call('/v1/moderation/reports',{headers:{authorization:`Bearer ${reviewerToken}`}});expect(queue.status).toBe(200);expect((await queue.json() as {data:Array<{id:string}>}).data.some(row=>row.id===reportData.id)).toBe(true);
    const resolved=await call(`/v1/moderation/reports/${reportData.id}/decision`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({decision:'DISMISSED',reason:'Reviewed test report; no production issue.'})});
    expect(resolved.status).toBe(200);expect((await resolved.json() as {data:{status:string}}).data.status).toBe('DISMISSED');
  });
  it.skipIf(!process.env.MARKETPLACE_UPDATE_PACKAGE_UNDER_TEST)('admin quarantine hides a published version from public catalog and package routes',async()=>{
    const bytes=await readFile(process.env.MARKETPLACE_UPDATE_PACKAGE_UNDER_TEST!);const {validatePackage}=await import('../src/validation/worker.js');const info=await validatePackage(bytes);
    const modId=String(info.manifest.id),version=String(info.manifest.version);
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/package`)).status).toBe(200);
    const denied=await call(`/v1/moderation/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/quarantine`,{method:'POST',headers:{authorization:`Bearer ${reviewerToken}`,'content-type':'application/json'},body:JSON.stringify({reason:'Test quarantine is admin-only.'})});expect(denied.status).toBe(403);
    const quarantined=await call(`/v1/moderation/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/quarantine`,{method:'POST',headers:{authorization:`Bearer ${adminToken}`,'content-type':'application/json'},body:JSON.stringify({reason:'Controlled integration quarantine verification.'})});
    expect(quarantined.status).toBe(200);expect((await quarantined.json() as {data:{status:string}}).data.status).toBe('QUARANTINED');
    expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/package`)).status).toBe(404);
    const original = await validatePackage(await packageBytes());
    if (original.manifest.id === modId) {
      expect((await call(`/v1/catalog/mods/${encodeURIComponent(modId)}`)).status).toBe(200);
      const originalUrl = `/v1/moderation/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(String(original.manifest.version))}/quarantine`;
      const originalDecision = await call(originalUrl, {
        method: 'POST',
        headers: {authorization: `Bearer ${adminToken}`, 'content-type': 'application/json'},
        body: JSON.stringify({reason: 'Quarantine remaining test release.'}),
      });
      expect(originalDecision.status).toBe(200);
    }
    // With every release quarantined, the public detail endpoint also hides
    // the mod instead of returning a listing with no available version.
    const details=await call(`/v1/catalog/mods/${encodeURIComponent(modId)}`);expect(details.status).toBe(404);
  });
  it('does not permit client registration to select an elevated role',async()=>{
    const response=await call('/v1/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'escalation@example.test',password:'some-strong-password-123',displayName:'Escalator',role:'ADMIN'})});
    expect(response.status).toBe(400);
  });
});
