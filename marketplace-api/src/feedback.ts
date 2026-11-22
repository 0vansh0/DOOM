import type { FastifyInstance } from 'fastify';
import { createHash } from 'node:crypto';
import { z } from 'zod';
import { client } from './db/index.js';
import { authenticate, requireRoles } from './security/auth.js';
import { loadConfig } from './config.js';

const config = loadConfig();

const short = z.string().trim().min(1).max(120);
const detail = z.string().trim().max(2000);
export const reportSchema = z.object({
  category:z.enum(['Bug','Suggestion','Mod compatibility','Marketplace','Unexpected behavior']),
  title:short, description:detail.min(1), steps:detail, expected:detail, actual:detail,
  appVersion:short, androidVersion:short, modId:z.string().max(100)
}).strict();
const status = z.enum(['open','in-progress','resolved']);
const triage = z.object({
  priority:z.enum(['P0','P1','P2','P3']), status, component:short,
  reproduction:z.enum(['unverified','reproduced','cannot reproduce']),
  regression_test:z.string().max(300), resolution:detail, assignee_id:z.uuid().nullable().optional()
}).strict();
const page = z.object({offset:z.coerce.number().int().min(0).max(100000).default(0),status:status.optional()}).strict();
const invalid = {error:{code:'INVALID_FEEDBACK',message:'Check the report fields and sharing consent.'}};
const missing = {error:{code:'NOT_FOUND',message:'Report unavailable.'}};

export function registerFeedback(app: FastifyInstance) {
  const reviewer = requireRoles('REVIEWER','ADMIN');
  const prune = () => pruneExpiredFeedback();
  let timer: ReturnType<typeof setInterval> | undefined;
  app.addHook('onReady', async () => {
    // Retention runs even when feedback has no incoming traffic.
    const sweep = () => { void prune().catch(() => app.log.warn('Feedback retention sweep failed')); };
    sweep();
    timer = setInterval(sweep, 3600000);
    timer.unref();
  });
  app.addHook('onClose', async () => clearInterval(timer));
  app.get('/v1/moderation/reviewers',{preHandler:reviewer},async()=>{
    const reviewers=await client`select id,email,role from users where role in ('REVIEWER','ADMIN') and disabled=false order by lower(email),id`;
    return {data:reviewers};
  });
  app.post('/v1/feedback',{preHandler:authenticate,bodyLimit:65536,config:{rateLimit:{max:config.FEEDBACK_RATE_LIMIT_MAX,timeWindow:config.FEEDBACK_RATE_LIMIT_WINDOW_MS}}},async(req,reply)=>{
    const input=z.object({consent:z.literal(true),report:reportSchema}).strict().safeParse(req.body);
    if(!input.success)return reply.code(400).send(invalid);
    const report=input.data.report;
    const digest=createHash('sha256').update(JSON.stringify(report)).digest('hex');
    await prune();
    const [row]=await client`insert into beta_feedback(owner_id,digest,report) values(${req.principal!.id},${digest},${JSON.stringify(report)}::jsonb)
      on conflict(owner_id,digest) do update set digest=excluded.digest returning id,status`;
    return reply.code(201).send({data:row});
  });
  for(const review of [false,true]){
    app.get(review?'/v1/moderation/feedback':'/v1/feedback',{preHandler:review?reviewer:authenticate},async(req,reply)=>{
      reply.header('Cache-Control','no-store');
      const q=page.safeParse(req.query);
      if(!q.success)return reply.code(400).send(invalid);
      const rows=await client`select id,report,status,priority,assignee_id,component,reproduction,regression_test,resolution,created_at,updated_at from beta_feedback
        where created_at > now()-interval '30 days' and (${review} or owner_id=${req.principal!.id})
        and (${q.data.status??null}::text is null or status=${q.data.status??null})
        order by created_at desc,id limit 25 offset ${q.data.offset}`;
      return {data:rows};
    });
  }

  app.get('/v1/feedback/:id',{preHandler:authenticate},async(req,reply)=>{
    reply.header('Cache-Control','no-store');
    const parsed=z.object({id:z.uuid()}).safeParse(req.params);
    if(!parsed.success)return reply.code(400).send(invalid);
    const [row]=await client`select id,report,status,priority,assignee_id,component,reproduction,regression_test,resolution,created_at,updated_at
      from beta_feedback where id=${parsed.data.id} and owner_id=${req.principal!.id} and created_at > now()-interval '30 days' limit 1`;
    return row?{data:row}:reply.code(404).send(missing);
  });

  app.get('/v1/moderation/feedback/:id',{preHandler:reviewer},async(req,reply)=>{
    reply.header('Cache-Control','no-store');
    const parsed=z.object({id:z.uuid()}).safeParse(req.params);
    if(!parsed.success)return reply.code(400).send(invalid);
    const [row]=await client`select id,owner_id,report,status,priority,assignee_id,component,reproduction,regression_test,resolution,created_at,updated_at
      from beta_feedback where id=${parsed.data.id} and created_at > now()-interval '30 days' limit 1`;
    return row?{data:row}:reply.code(404).send(missing);
  });

  app.post('/v1/moderation/feedback/:id',{preHandler:reviewer},async(req,reply)=>{
    const input=triage.safeParse(req.body);
    const id=z.object({id:z.uuid()}).safeParse(req.params);
    if(!input.success||!id.success)return reply.code(400).send(invalid);
    const t=input.data;
    const result=await client.begin(async tx=>{
      const [previous]=await tx`select status,priority,reproduction,assignee_id from beta_feedback
        where id=${id.data.id} and created_at > now()-interval '30 days' for update`;
      if(!previous)return null;
      if(!allowedFeedbackTransition(String(previous.status),t.status))return {invalidTransition:true as const};
      const assigneeId=t.assignee_id === undefined ? previous.assignee_id : t.assignee_id;
      if(assigneeId !== null){
        const [assignee]=await tx`select id from users where id=${assigneeId} and role in ('REVIEWER','ADMIN') and disabled=false for key share`;
        if(!assignee)return {invalidAssignee:true as const};
      }
      const [updated]=await tx`update beta_feedback set status=${t.status},priority=${t.priority},assignee_id=${assigneeId},component=${t.component},reproduction=${t.reproduction},regression_test=${t.regression_test},resolution=${t.resolution},updated_at=now()
        where id=${id.data.id} returning id,status,priority,assignee_id,component,reproduction,regression_test,resolution,created_at,updated_at`;
      await tx`insert into audit_events(actor_id,action,subject_type,subject_id,details)
        values(${req.principal!.id},'FEEDBACK_TRIAGE_UPDATED','feedback',${id.data.id},${JSON.stringify({
          previousStatus:String(previous.status),newStatus:t.status,
          previousPriority:String(previous.priority),newPriority:t.priority,
          previousReproduction:String(previous.reproduction),newReproduction:t.reproduction,
          previousAssigneeId:previous.assignee_id == null ? null : String(previous.assignee_id),newAssigneeId:assigneeId
        })}::jsonb)`;
      return {row:updated};
    });
    if(!result)return reply.code(404).send(missing);
    if('invalidTransition' in result)return reply.code(409).send({error:{code:'INVALID_STATE',message:'Reopen a resolved report before moving it back into progress.'}});
    if('invalidAssignee' in result)return reply.code(400).send({error:{code:'INVALID_ASSIGNEE',message:'Assignee must be an active reviewer or administrator.'}});
    return {data:result.row};
  });
  app.post('/v1/feedback/:id/delete',{preHandler:authenticate},async(req,reply)=>{
    const id=z.object({id:z.uuid()}).safeParse(req.params);
    if(!id.success)return reply.code(400).send(invalid);
    const rows=await client.begin(tx=>tx`delete from beta_feedback where id=${id.data.id} and owner_id=${req.principal!.id} returning id`);
    return rows.length?{data:rows[0]}:reply.code(404).send(missing);
  });
}

const allowedFeedbackTransition=(from:string,to:string)=>{
  if(from===to)return true;
  if(from==='open')return to==='in-progress'||to==='resolved';
  if(from==='in-progress')return to==='open'||to==='resolved';
  if(from==='resolved')return to==='open';
  return false;
};

export async function pruneExpiredFeedback(cutoff=new Date(Date.now()-30*24*60*60*1000)) {
  // postgres.js does not serialize Date instances through this typed SQL
  // parameter path. ISO 8601 text is accepted by PostgreSQL timestamptz.
  await client.begin(tx=>tx`delete from beta_feedback where created_at <= ${cutoff.toISOString()}::timestamptz`);
}

