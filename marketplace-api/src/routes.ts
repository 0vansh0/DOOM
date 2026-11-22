import { createHash, randomUUID } from 'node:crypto';
import { and, desc, eq, ilike, isNotNull, sql } from 'drizzle-orm';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import semver from 'semver';
import { client, db } from './db/index.js';
import { authenticate, requireRoles } from './security/auth.js';
import { ArtifactStorage } from './storage/s3.js';
import { validatePackage } from './validation/worker.js';
import { allowedTransitions, assertTransition } from './domain/transitions.js';
import type { AppSubmissionStatus } from './domain/types.js';
import { users, developerProfiles, mods, modVersions, submissions, validationResults, moderationDecisions, userReports, auditEvents } from './db/schema.js';
import { loadConfig } from './config.js';
import { validatorVersion } from 'doom-mod-cli/marketplace';

const config = loadConfig();
const reportBody = z.object({ reason: z.string().trim().min(10).max(1000), version: z.string().max(64).optional() }).strict();
const moderateBody = z.object({ decision: z.enum(['APPROVE','REJECT']), reason: z.string().trim().min(8).max(1000) }).strict();
const publishBody = z.object({ category: z.string().trim().min(1).max(40).default('other') }).strict();
const profileBody = z.object({ displayName: z.string().trim().min(2).max(80), bio: z.string().trim().max(500).default(''), website: z.string().url().max(2048).nullable().optional() }).strict();
const requiredMigrations = ['0001_initial.sql', '0002_feedback.sql', '0003_feedback_triage.sql', '0004_feedback_assignee.sql'] as const;
const readinessTimeoutMs = 3_000;

async function checkDatabaseReadiness() {
  await client.begin(async transaction => {
    await transaction`SET LOCAL statement_timeout = '2500ms'`;
    await transaction`SELECT 1`;
    const applied = await transaction`SELECT name FROM schema_migrations`;
    const names = new Set(applied.map(row => row.name));
    if (requiredMigrations.some(name => !names.has(name))) throw new Error('Required migrations are not applied.');
  });
}

async function checkReadiness(storage: ArtifactStorage): Promise<boolean> {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const dependencyChecks = Promise.all([checkDatabaseReadiness(), storage.health(controller.signal)]);
  try {
    await Promise.race([
      dependencyChecks,
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => {
          controller.abort();
          reject(new Error('Readiness dependency check timed out.'));
        }, readinessTimeoutMs);
      }),
    ]);
    return true;
  } catch {
    controller.abort();
    return false;
  } finally {
    if (timer) clearTimeout(timer);
  }
}

export async function registerRoutes(app: FastifyInstance, storage: ArtifactStorage) {
  app.get('/health', async () => ({ status: 'ok' }));
  const readyHandler = async (_request: unknown, reply: { code: (status: number) => { send: (body: unknown) => unknown } }) => {
    if (await checkReadiness(storage)) return { status: 'ready' };
    return reply.code(503).send({ error: { code: 'NOT_READY', message: 'Required dependencies are unavailable.' } });
  };
  app.get('/ready', readyHandler);
  // Keep the prior readiness URL available for existing local integrations.
  app.get('/healthz', readyHandler);

  app.get('/v1/catalog/mods', async (request, reply) => {
    const q = request.query as Record<string,string|undefined>;
    const query = z.object({ search: z.string().max(80).optional(), category: z.string().max(40).optional(), capability: z.string().max(80).optional(), cursor: z.string().uuid().optional(), limit: z.coerce.number().int().min(1).max(50).default(20), sort: z.enum(['recent','name']).default('recent') }).safeParse(q);
    if (!query.success) return reply.code(400).send({ error: { code: 'INVALID_QUERY', message: 'Catalog query is invalid.' } });
    const { search, category, capability, cursor, limit, sort } = query.data;
    const cursorFilter = cursor ? (sort === 'name'
      ? sql`(lower(${mods.name}), ${modVersions.id}) > ((SELECT lower(m.name) FROM mod_versions mv JOIN mods m ON m.id=mv.mod_id WHERE mv.id=${cursor}), ${cursor}::uuid)`
      : sql`(${modVersions.publishedAt}, ${modVersions.id}) < ((SELECT published_at FROM mod_versions WHERE id=${cursor}), ${cursor}::uuid)`) : undefined;
    const where = and(isNotNull(modVersions.publishedAt), sql`${modVersions.quarantinedAt} IS NULL`,
      search ? ilike(mods.name, `%${escapeLike(search)}%`) : undefined, category ? eq(mods.category, category) : undefined,
      capability ? sql`${modVersions.capabilities} ? ${capability}` : undefined,
      cursorFilter);
    const rows = await db.select({ id: mods.id, name: mods.name, summary: mods.summary, category: mods.category, developer: developerProfiles.displayName,
      version: modVersions.version, manifestSchema: modVersions.manifestSchema, definitionVersion: modVersions.definitionVersion,
      minAndroidApi: modVersions.minAndroidApi, capabilities: modVersions.capabilities, compatibility: modVersions.compatibility,
      sha256: modVersions.sha256, publishedAt: modVersions.publishedAt, versionId: modVersions.id })
      .from(modVersions).innerJoin(mods, eq(modVersions.modId, mods.id)).innerJoin(users, eq(mods.ownerId, users.id))
      .leftJoin(developerProfiles, eq(developerProfiles.userId, users.id)).where(where)
      .orderBy(sort === 'name' ? sql`lower(${mods.name})` : desc(modVersions.publishedAt), sort === 'name' ? modVersions.id : desc(modVersions.id)).limit(limit + 1);
    const hasMore = rows.length > limit; const page = rows.slice(0, limit);
    return { data: page, pagination: { limit, nextCursor: hasMore ? page.at(-1)?.versionId : null } };
  });
  app.get('/v1/catalog/mods/:modId', async (request, reply) => {
    const { modId } = request.params as { modId: string };
    const rows = await db.select({ id: mods.id, name: mods.name, summary: mods.summary, category: mods.category, developer: developerProfiles.displayName,
      version: modVersions.version, manifestSchema: modVersions.manifestSchema, definitionVersion: modVersions.definitionVersion,
      minAndroidApi: modVersions.minAndroidApi, capabilities: modVersions.capabilities, compatibility: modVersions.compatibility,
      sha256: modVersions.sha256, publishedAt: modVersions.publishedAt, versionId: modVersions.id })
      .from(modVersions).innerJoin(mods, eq(modVersions.modId, mods.id)).innerJoin(users, eq(mods.ownerId, users.id))
      .leftJoin(developerProfiles, eq(developerProfiles.userId, users.id)).where(and(eq(mods.id, modId), isNotNull(modVersions.publishedAt), sql`${modVersions.quarantinedAt} IS NULL`))
      .orderBy(desc(modVersions.publishedAt));
    if (!rows.length) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Published mod was not found.' } });
    return { data: { mod: { id: rows[0]!.id, name: rows[0]!.name, summary: rows[0]!.summary, category: rows[0]!.category, developer: rows[0]!.developer }, versions: rows } };
  });
  app.get('/v1/catalog/developers/:userId', async (request, reply) => {
    const { userId } = request.params as { userId: string };
    const [profile] = await db.select({ userId: users.id, displayName: developerProfiles.displayName, bio: developerProfiles.bio, website: developerProfiles.website, createdAt: developerProfiles.createdAt })
      .from(developerProfiles).innerJoin(users, eq(developerProfiles.userId, users.id)).where(eq(users.id,userId)).limit(1);
    if (!profile) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Developer was not found.' } });
    return { data: profile };
  });
  app.patch('/v1/developer/profile', { preHandler: authenticate }, async (request, reply) => {
    const parsed = profileBody.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: { code: 'INVALID_INPUT', message: 'Developer profile fields are invalid.' } });
    const [profile] = await db.update(developerProfiles).set({ displayName: parsed.data.displayName, bio: parsed.data.bio, website: parsed.data.website ?? null })
      .where(eq(developerProfiles.userId, request.principal!.id)).returning({ userId: developerProfiles.userId, displayName: developerProfiles.displayName, bio: developerProfiles.bio, website: developerProfiles.website });
    if (!profile) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Developer profile was not found.' } });
    await db.insert(auditEvents).values({ actorId: request.principal!.id, action: 'DEVELOPER_PROFILE_UPDATED', subjectType: 'developer_profile', subjectId: profile.userId, details: {} });
    return { data: profile };
  });

  app.post('/v1/submissions', { preHandler: authenticate }, async (request, reply) => {
    let part;
    try { part = await request.file({ limits: { files: 1, fileSize: config.UPLOAD_MAX_BYTES, fields: 0, parts: 1 } }); }
    catch { return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } }); }
    if (!part) return reply.code(400).send({ error: { code: 'PACKAGE_REQUIRED', message: 'A .doommod package file is required.' } });
    const chunks: Buffer[] = []; let total = 0;
    for await (const chunk of part.file) { total += chunk.length; if (total > config.UPLOAD_MAX_BYTES) return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } }); chunks.push(Buffer.from(chunk)); }
    if (part.file.truncated) return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } });
    if(part.fieldname!=='package')return reply.code(400).send({error:{code:'PACKAGE_REQUIRED',message:'Upload the archive in the multipart field named package.'}});
    const bytes = Buffer.concat(chunks); const quarantineKey = `incoming/${randomUUID()}`;
    const submissionId = randomUUID();
    try {
      const quotaDay=new Date().toISOString().slice(0,10);
      const quota=await db.execute(sql`INSERT INTO upload_quotas (owner_id,quota_day,upload_count,byte_count) VALUES (${request.principal!.id},${quotaDay},1,${bytes.length})
        ON CONFLICT (owner_id,quota_day) DO UPDATE SET upload_count=upload_quotas.upload_count+1,byte_count=upload_quotas.byte_count+EXCLUDED.byte_count,updated_at=now()
        WHERE upload_quotas.upload_count < ${config.UPLOADS_PER_DAY} AND upload_quotas.byte_count+EXCLUDED.byte_count <= ${config.UPLOAD_BYTES_PER_DAY}
        RETURNING owner_id`);
      if(!quota.length)return reply.code(429).send({error:{code:'UPLOAD_QUOTA_EXCEEDED',message:'Daily upload count or byte quota has been reached.'}});
      const packageInfo = await validatePackage(bytes);
      const [duplicate] = await db.select({id:submissions.id}).from(submissions).where(and(eq(submissions.ownerId,request.principal!.id),eq(submissions.sha256,packageInfo.packageSha256))).limit(1);
      if (duplicate) return reply.code(409).send({error:{code:'DUPLICATE_SUBMISSION',message:'This exact package was already submitted by this account.'}});
      await storage.putQuarantine(quarantineKey, bytes, packageInfo.packageSha256);
      const manifest = packageInfo.manifest;
      const row = await db.transaction(async tx=>{
        const [saved] = await tx.insert(submissions).values({ id: submissionId, ownerId: request.principal!.id, modId: String(manifest.id), status: 'UPLOADED', quarantineKey,
          originalName: safeDisplayName(part.filename), sha256: packageInfo.packageSha256, byteSize: packageInfo.size, manifest, definition: packageInfo.definition,
          validationSummary: { passed: true, validatorVersion: packageInfo.validatorVersion } }).returning();
        await tx.insert(validationResults).values({submissionId,validatorVersion:packageInfo.validatorVersion,passed:true,diagnostics:[]});
        await tx.insert(auditEvents).values({actorId:request.principal!.id,action:'PACKAGE_UPLOADED',subjectType:'submission',subjectId:saved!.id,details:{sha256:packageInfo.packageSha256,bytes:packageInfo.size}});
        return saved!;
      });
      return reply.code(201).send({ data: { id: row!.id, status: row!.status, modId: row!.modId, version: manifest.version, sha256: packageInfo.packageSha256 } });
    } catch (error) {
      await storage.deleteQuarantine(quarantineKey).catch(() => undefined);
      if (isUniqueViolation(error)) return reply.code(409).send({error:{code:'DUPLICATE_SUBMISSION',message:'This exact package was already submitted by this account.'}});
      const message = safeValidationMessage(error);
      await db.transaction(async tx=>{
        await tx.insert(submissions).values({ id: submissionId, ownerId: request.principal!.id, status: 'VALIDATION_FAILED', originalName: safeDisplayName(part.filename), byteSize: bytes.length,
          validationSummary: { passed: false } });
        await tx.insert(validationResults).values({ submissionId, validatorVersion, passed: false, diagnostics: [{ code: 'PACKAGE_INVALID', message }] });
        await tx.insert(auditEvents).values({actorId:request.principal!.id,action:'PACKAGE_VALIDATION_FAILED',subjectType:'submission',subjectId:submissionId,details:{bytes:bytes.length}});
      }).catch(error=>request.log.error({err:error},'failed to persist package rejection record'));
      return reply.code(422).send({ error: { code: 'PACKAGE_INVALID', message }, data: { submissionId, status: 'VALIDATION_FAILED' } });
    }
  });
  app.post('/v1/submissions/drafts', { preHandler:authenticate }, async(request,reply)=>{
    const schema=z.object({modId:z.string().regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/).max(64).optional()}).strict();
    const parsed=schema.safeParse(request.body??{});if(!parsed.success)return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Draft metadata is invalid.'}});
    const [draft]=await db.insert(submissions).values({ownerId:request.principal!.id,modId:parsed.data.modId,status:'DRAFT'}).returning({id:submissions.id,status:submissions.status});
    await db.insert(auditEvents).values({actorId:request.principal!.id,action:'SUBMISSION_DRAFT_CREATED',subjectType:'submission',subjectId:draft!.id,details:{}});
    return reply.code(201).send({data:draft});
  });
  app.patch('/v1/submissions/:id/draft', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ modId: z.union([z.string().regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/).max(64), z.null()]) }).strict();
    const parsed = schema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: { code: 'INVALID_INPUT', message: 'Draft metadata is invalid.' } });
    const { id } = request.params as { id: string };
    const [row] = await db.update(submissions).set({ modId: parsed.data.modId, updatedAt: new Date() })
      .where(and(eq(submissions.id, id), eq(submissions.ownerId, request.principal!.id), eq(submissions.status, 'DRAFT')))
      .returning({ id: submissions.id, modId: submissions.modId, status: submissions.status });
    if (!row) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Editable draft was not found.' } });
    await db.insert(auditEvents).values({ actorId: request.principal!.id, action: 'SUBMISSION_DRAFT_UPDATED', subjectType: 'submission', subjectId: id, details: {} });
    return { data: row };
  });
  app.post('/v1/submissions/:id/package', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const principal = request.principal!;
    const [draft] = await db.select().from(submissions).where(eq(submissions.id, id)).limit(1);
    if (!draft || draft.ownerId !== principal.id) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Draft was not found.' } });
    if (draft.status !== 'DRAFT') return reply.code(409).send({ error: { code: 'INVALID_STATE', message: 'Only an editable draft can receive a package.' } });
    let part;
    try { part = await request.file({ limits: { files: 1, fileSize: config.UPLOAD_MAX_BYTES, fields: 0, parts: 1 } }); }
    catch { return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } }); }
    if (!part || part.fieldname !== 'package') return reply.code(400).send({ error: { code: 'PACKAGE_REQUIRED', message: 'Upload the archive in the multipart field named package.' } });
    const chunks: Buffer[] = []; let total = 0;
    for await (const chunk of part.file) { total += chunk.length; if (total > config.UPLOAD_MAX_BYTES) return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } }); chunks.push(Buffer.from(chunk)); }
    if (part.file.truncated) return reply.code(413).send({ error: { code: 'UPLOAD_LIMIT', message: 'Upload exceeds the configured package limit.' } });
    const bytes = Buffer.concat(chunks); const quarantineKey = `incoming/${randomUUID()}`;
    let packageInfo: Awaited<ReturnType<typeof validatePackage>>;
    try { packageInfo = await validatePackage(bytes); }
    catch (error) { return reply.code(422).send({ error: { code: 'PACKAGE_INVALID', message: safeValidationMessage(error) } }); }
    const manifest = packageInfo.manifest;
    if (draft.modId && draft.modId !== String(manifest.id)) return reply.code(409).send({ error: { code: 'DRAFT_MOD_MISMATCH', message: 'Package mod ID does not match this draft.' } });
    try {
      const [duplicate] = await db.select({ id: submissions.id }).from(submissions).where(and(eq(submissions.ownerId, principal.id), eq(submissions.sha256, packageInfo.packageSha256))).limit(1);
      if (duplicate) return reply.code(409).send({ error: { code: 'DUPLICATE_SUBMISSION', message: 'This exact package was already submitted by this account.' } });
      const quotaDay = new Date().toISOString().slice(0, 10);
      const quota = await db.execute(sql`INSERT INTO upload_quotas (owner_id,quota_day,upload_count,byte_count) VALUES (${principal.id},${quotaDay},1,${bytes.length})
        ON CONFLICT (owner_id,quota_day) DO UPDATE SET upload_count=upload_quotas.upload_count+1,byte_count=upload_quotas.byte_count+EXCLUDED.byte_count,updated_at=now()
        WHERE upload_quotas.upload_count < ${config.UPLOADS_PER_DAY} AND upload_quotas.byte_count+EXCLUDED.byte_count <= ${config.UPLOAD_BYTES_PER_DAY} RETURNING owner_id`);
      if (!quota.length) return reply.code(429).send({ error: { code: 'UPLOAD_QUOTA_EXCEEDED', message: 'Daily upload count or byte quota has been reached.' } });
      await storage.putQuarantine(quarantineKey, bytes, packageInfo.packageSha256);
      const updated = await db.transaction(async tx => {
        const [row] = await tx.update(submissions).set({ modId: String(manifest.id), status: 'UPLOADED', quarantineKey,
          originalName: safeDisplayName(part.filename), sha256: packageInfo.packageSha256, byteSize: packageInfo.size, manifest, definition: packageInfo.definition,
          validationSummary: { passed: true, validatorVersion: packageInfo.validatorVersion }, updatedAt: new Date() })
          .where(and(eq(submissions.id, id), eq(submissions.status, 'DRAFT'))).returning({ id: submissions.id, status: submissions.status, modId: submissions.modId, sha256: submissions.sha256 });
        if (!row) throw new Error('Draft changed while the upload was being processed.');
        await tx.insert(validationResults).values({ submissionId: id, validatorVersion: packageInfo.validatorVersion, passed: true, diagnostics: [] });
        await tx.insert(auditEvents).values({ actorId: principal.id, action: 'PACKAGE_ATTACHED_TO_DRAFT', subjectType: 'submission', subjectId: id, details: { sha256: packageInfo.packageSha256, bytes: packageInfo.size } });
        return row;
      });
      return reply.code(200).send({ data: { ...updated, version: manifest.version } });
    } catch (error) {
      await storage.deleteQuarantine(quarantineKey).catch(() => undefined);
      if (isUniqueViolation(error)) return reply.code(409).send({ error: { code: 'DUPLICATE_SUBMISSION', message: 'This exact package was already submitted by this account.' } });
      request.log.error({ err: error, submissionId: id }, 'failed to attach validated package to draft');
      return reply.code(503).send({ error: { code: 'UPLOAD_STORAGE_UNAVAILABLE', message: 'The package could not be committed. The draft remains available to retry.' } });
    }
  });
  app.get('/v1/submissions', { preHandler: authenticate }, async request => {
    const principal = request.principal!;
    const rows = await db.select({ id: submissions.id, modId: submissions.modId, status: submissions.status, sha256: submissions.sha256,
      createdAt: submissions.createdAt, updatedAt: submissions.updatedAt, validationSummary: submissions.validationSummary })
      .from(submissions).where(principal.role === 'ADMIN' || principal.role === 'REVIEWER' ? undefined : eq(submissions.ownerId, principal.id)).orderBy(desc(submissions.createdAt)).limit(100);
    return { data: rows };
  });
  app.get('/v1/submissions/:id', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string }; const principal = request.principal!;
    const [row] = await db.select().from(submissions).where(eq(submissions.id,id)).limit(1);
    if (!row || (row.ownerId !== principal.id && !['ADMIN','REVIEWER'].includes(principal.role))) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Submission was not found.' } });
    const validations = await db.select().from(validationResults).where(eq(validationResults.submissionId,id)).orderBy(desc(validationResults.createdAt));
    const decisions = await db.select({ decision: moderationDecisions.decision, reason: moderationDecisions.reason, createdAt: moderationDecisions.createdAt })
      .from(moderationDecisions).where(eq(moderationDecisions.submissionId, id)).orderBy(desc(moderationDecisions.createdAt));
    return { data: { ...row, quarantineKey: undefined, validations, moderationDecisions: decisions } };
  });
  app.post('/v1/submissions/:id/validate', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string }; const principal = request.principal!;
    const [row] = await db.select().from(submissions).where(eq(submissions.id,id)).limit(1);
    if (!row || row.ownerId !== principal.id) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Submission was not found.' } });
    if (!row.quarantineKey || row.status!=='UPLOADED') return reply.code(409).send({ error: { code: 'INVALID_STATE', message: 'Only uploaded submissions can be validated.' } });
    await transitionSubmission(row.id, row.status, 'VALIDATING');
    try {
      const bytes = await storage.readQuarantine(row.quarantineKey); const packageInfo = await validatePackage(bytes);
      if (row.sha256 !== packageInfo.packageSha256) throw new Error('Stored package checksum does not match the uploaded checksum.');
      await db.transaction(async tx=>{
        await tx.insert(validationResults).values({ submissionId:id, validatorVersion:packageInfo.validatorVersion, passed:true, diagnostics:[] });
        await tx.update(submissions).set({ status:'PENDING_REVIEW', manifest:packageInfo.manifest, definition:packageInfo.definition, validationSummary:{ passed:true, validatorVersion:packageInfo.validatorVersion }, updatedAt:new Date() }).where(eq(submissions.id,id));
        await tx.insert(auditEvents).values({actorId:principal.id,action:'PACKAGE_VALIDATED',subjectType:'submission',subjectId:id,details:{passed:true}});
      });
      return { data: { id, status:'PENDING_REVIEW', sha256:packageInfo.packageSha256 } };
    } catch (error) {
      const message = safeValidationMessage(error);
      await db.transaction(async tx=>{
        await tx.insert(validationResults).values({ submissionId:id, validatorVersion, passed:false, diagnostics:[{ code:'PACKAGE_INVALID',message }] });
        await tx.update(submissions).set({ status:'VALIDATION_FAILED', quarantineKey:null, validationSummary:{ passed:false }, updatedAt:new Date() }).where(eq(submissions.id,id));
        await tx.insert(auditEvents).values({actorId:principal.id,action:'PACKAGE_VALIDATION_FAILED',subjectType:'submission',subjectId:id,details:{}});
      });
      await storage.deleteQuarantine(row.quarantineKey).catch(storageError=>request.log.warn({err:storageError},'failed quarantine cleanup after validation rejection'));
      return reply.code(422).send({ error:{ code:'PACKAGE_INVALID',message }, data:{ id,status:'VALIDATION_FAILED' } });
    }
  });

  app.get('/v1/moderation/submissions', { preHandler: requireRoles('REVIEWER','ADMIN') }, async (_request) => {
    const rows = await db.select({ id:submissions.id, modId:submissions.modId, status:submissions.status, ownerId:submissions.ownerId, createdAt:submissions.createdAt, name:sql<string>`${submissions.manifest}->>'name'`, version:sql<string>`${submissions.manifest}->>'version'` })
      .from(submissions).where(eq(submissions.status,'PENDING_REVIEW')).orderBy(submissions.createdAt).limit(100);
    return { data:rows };
  });
  app.get('/v1/moderation/submissions/:id/package', {preHandler:requireRoles('REVIEWER','ADMIN')}, async(request,reply)=>{
    const {id}=request.params as {id:string};
    const [row]=await db.select({status:submissions.status,quarantineKey:submissions.quarantineKey}).from(submissions).where(eq(submissions.id,id)).limit(1);
    if(!row||row.status!=='PENDING_REVIEW'||!row.quarantineKey)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Review package was not found.'}});
    return {data:{downloadUrl:await storage.quarantineReviewUrl(row.quarantineKey),urlExpiresInSeconds:300}};
  });
  app.get('/v1/moderation/submissions/:id/package/download', { preHandler: requireRoles('REVIEWER','ADMIN') }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const [row] = await db.select({ status: submissions.status, quarantineKey: submissions.quarantineKey, sha256: submissions.sha256, originalName: submissions.originalName })
      .from(submissions).where(eq(submissions.id, id)).limit(1);
    if (!row || row.status !== 'PENDING_REVIEW' || !row.quarantineKey || !row.sha256) return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Review package was not found.' } });
    try {
      const bytes = await storage.readQuarantine(row.quarantineKey);
      if (bytes.length > config.UPLOAD_MAX_BYTES || createHash('sha256').update(bytes).digest('hex') !== row.sha256) {
        request.log.error({ submissionId: id }, 'quarantine package failed stored checksum verification');
        return reply.code(503).send({ error: { code: 'ARTIFACT_INTEGRITY_ERROR', message: 'The quarantined artifact could not be verified.' } });
      }
      return reply.type('application/vnd.doom.mod').header('cache-control', 'private, no-store').header('content-length', String(bytes.length))
        .header('content-disposition', `attachment; filename="${safeDisplayName(row.originalName)}"`).header('x-doom-sha256', row.sha256).send(bytes);
    } catch (error) {
      request.log.error({ err: error, submissionId: id }, 'review package storage read failed');
      return reply.code(503).send({ error: { code: 'STORAGE_UNAVAILABLE', message: 'The review package is temporarily unavailable.' } });
    }
  });
  app.post('/v1/moderation/submissions/:id/decision', { preHandler: requireRoles('REVIEWER','ADMIN') }, async (request, reply) => {
    const parsed = moderateBody.safeParse(request.body); if(!parsed.success) return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Decision and reason are required.'}});
    const { id } = request.params as { id:string }; const principal=request.principal!;
    const [row]=await db.select().from(submissions).where(eq(submissions.id,id)).limit(1);
    if(!row) return reply.code(404).send({error:{code:'NOT_FOUND',message:'Submission was not found.'}});
    if(row.ownerId===principal.id) return reply.code(403).send({error:{code:'SELF_REVIEW_DENIED',message:'Authors cannot review their own submissions.'}});
    if(row.status!=='PENDING_REVIEW') return reply.code(409).send({error:{code:'INVALID_STATE',message:'Only pending submissions can be reviewed.'}});
    const to=parsed.data.decision==='APPROVE'?'APPROVED':'REJECTED';
    try {
      await db.transaction(async tx=>{
        assertTransition(row.status as AppSubmissionStatus,to);
        // Claim the pending state atomically. A second reviewer racing this
        // decision must observe the changed state instead of recording a
        // contradictory moderation decision.
        const [claimed]=await tx.update(submissions).set({status:to,updatedAt:new Date()})
          .where(and(eq(submissions.id,id),eq(submissions.status,'PENDING_REVIEW')))
          .returning({id:submissions.id});
        if(!claimed)throw new SubmissionReviewConflict();
        await tx.insert(moderationDecisions).values({submissionId:id,reviewerId:principal.id,decision:parsed.data.decision,reason:parsed.data.reason});
        await tx.insert(auditEvents).values({actorId:principal.id,action:`SUBMISSION_${to}`,subjectType:'submission',subjectId:id,details:{}});
      });
    } catch(error) {
      if(error instanceof SubmissionReviewConflict)return reply.code(409).send({error:{code:'INVALID_STATE',message:'This submission was already reviewed.'}});
      throw error;
    }
    return {data:{id,status:to}};
  });
  app.post('/v1/submissions/:id/publish', { preHandler:authenticate }, async(request,reply)=>{
    const {id}=request.params as {id:string}; const principal=request.principal!;
    const parsed=publishBody.safeParse(request.body??{}); if(!parsed.success)return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Publication metadata is invalid.'}});
    const [row]=await db.select().from(submissions).where(eq(submissions.id,id)).limit(1);
    if(!row||row.ownerId!==principal.id)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Submission was not found.'}});
    if(row.status!=='APPROVED'||!row.quarantineKey||!row.sha256||!row.manifest)return reply.code(409).send({error:{code:'INVALID_STATE',message:'Only approved submissions can be published.'}});
    const manifest=row.manifest as Record<string,unknown>; const definition=row.definition as Record<string,unknown>;
    const modId=String(manifest.id), version=String(manifest.version);
    if(!semver.valid(version,{loose:false}))return reply.code(422).send({error:{code:'INVALID_VERSION',message:'Package version must be a valid semantic version.'}});
    const [ownerMod]=await db.select().from(mods).where(eq(mods.id,modId)).limit(1);
    if(ownerMod&&ownerMod.ownerId!==principal.id)return reply.code(409).send({error:{code:'MOD_OWNED_BY_ANOTHER_DEVELOPER',message:'This mod identifier belongs to another developer.'}});
    const [existing]=await db.select({id:modVersions.id}).from(modVersions).where(and(eq(modVersions.modId,modId),eq(modVersions.version,version))).limit(1);
    if(existing)return reply.code(409).send({error:{code:'VERSION_EXISTS',message:'That mod version already exists and cannot be overwritten.'}});
    const objectKey=`releases/${modId}/${version}/${row.sha256}.doommod`;
    try { await storage.publish(row.quarantineKey,objectKey); }
    catch { return reply.code(503).send({error:{code:'STORAGE_UNAVAILABLE',message:'Package artifact could not be published.'}}); }
    try {
      await db.transaction(async tx=>{
        if(!ownerMod) await tx.insert(mods).values({id:modId,ownerId:principal.id,name:String(manifest.name),summary:String(manifest.description),category:parsed.data.category});
        const [created]=await tx.insert(modVersions).values({modId,version,manifestSchema:Number(manifest.schemaVersion),definitionVersion:Number(definition.definitionVersion),minAndroidApi:Number(manifest.minAndroidApi),capabilities:manifest.capabilities as string[],compatibility:{minAndroidApi:Number(manifest.minAndroidApi)},sha256:row.sha256!,objectKey,publishedAt:new Date()}).returning({id:modVersions.id});
        assertTransition(row.status as AppSubmissionStatus,'PUBLISHED');
        await tx.update(submissions).set({status:'PUBLISHED',modId,updatedAt:new Date()}).where(eq(submissions.id,id));
        await tx.insert(auditEvents).values({actorId:principal.id,action:'MOD_VERSION_PUBLISHED',subjectType:'mod_version',subjectId:created!.id,details:{modId,version,sha256:row.sha256}});
      });
    } catch(error) {
      app.log.error({err:error,submissionId:id},'publication persistence failed after artifact copy');
      return reply.code(409).send({error:{code:'PUBLICATION_CONFLICT',message:'Publication could not be committed. The immutable artifact will be reconciled by the storage cleanup task.'}});
    }
    await storage.deleteQuarantine(row.quarantineKey).catch(error=>app.log.warn({err:error},'published quarantine cleanup failed'));
    return reply.code(201).send({data:{modId,version,sha256:row.sha256,publicationStatus:'PUBLISHED'}});
  });
  app.get('/v1/catalog/mods/:modId/versions/:version/download', async(request,reply)=>{
    const {modId,version}=request.params as {modId:string;version:string};
    const [row]=await db.select({version:modVersions.version,sha256:modVersions.sha256,objectKey:modVersions.objectKey,publishedAt:modVersions.publishedAt,quarantinedAt:modVersions.quarantinedAt})
      .from(modVersions).where(and(eq(modVersions.modId,modId),eq(modVersions.version,version),isNotNull(modVersions.publishedAt),sql`${modVersions.quarantinedAt} IS NULL`)).limit(1);
    if(!row)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Published package version was not found.'}});
    return {data:{modId,version,sha256:row.sha256,downloadUrl:`/v1/catalog/mods/${encodeURIComponent(modId)}/versions/${encodeURIComponent(version)}/package`}};
  });
  app.get('/v1/catalog/mods/:modId/versions/:version/package', async(request,reply)=>{
    const {modId,version}=request.params as {modId:string;version:string};
    const [row]=await db.select({objectKey:modVersions.objectKey,sha256:modVersions.sha256})
      .from(modVersions).where(and(eq(modVersions.modId,modId),eq(modVersions.version,version),isNotNull(modVersions.publishedAt),sql`${modVersions.quarantinedAt} IS NULL`)).limit(1);
    if(!row)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Published package version was not found.'}});
    try{
      const bytes=await storage.readPublished(row.objectKey);
      if(bytes.length>10*1024*1024)return reply.code(500).send({error:{code:'ARTIFACT_INVALID',message:'Published artifact exceeds the supported limit.'}});
      return reply.type('application/vnd.doom.mod').header('cache-control','private, no-store').header('content-length',String(bytes.length))
        .header('x-doom-sha256',row.sha256).send(bytes);
    }catch{return reply.code(503).send({error:{code:'STORAGE_UNAVAILABLE',message:'Published package is temporarily unavailable.'}});}
  });
  app.post('/v1/catalog/mods/:modId/reports', {preHandler:authenticate}, async(request,reply)=>{
    const parsed=reportBody.safeParse(request.body);if(!parsed.success)return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Report reason is invalid.'}});
    const {modId}=request.params as {modId:string};const [exists]=await db.select({id:mods.id}).from(mods).where(eq(mods.id,modId)).limit(1);
    if(!exists)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Mod was not found.'}});
    const report=await db.transaction(async tx=>{
      const [created]=await tx.insert(userReports).values({reporterId:request.principal!.id,modId,version:parsed.data.version,reason:parsed.data.reason}).returning({id:userReports.id});
      await tx.insert(auditEvents).values({actorId:request.principal!.id,action:'MOD_REPORTED',subjectType:'mod',subjectId:modId,details:{reportId:created!.id}});
      return created!;
    });
    return reply.code(201).send({data:{id:report.id,status:'OPEN'}});
  });
  app.get('/v1/moderation/reports', {preHandler:requireRoles('REVIEWER','ADMIN')}, async()=>{
    const rows=await db.select().from(userReports).where(eq(userReports.status,'OPEN')).orderBy(userReports.createdAt).limit(100);return {data:rows};
  });
  app.get('/v1/moderation/audit', { preHandler: requireRoles('REVIEWER','ADMIN') }, async (request, reply) => {
    const query = z.object({ limit: z.coerce.number().int().min(1).max(100).default(50) }).safeParse(request.query);
    if (!query.success) return reply.code(400).send({ error: { code: 'INVALID_QUERY', message: 'Audit query is invalid.' } });
    const rows = await db.select({ id: auditEvents.id, actorId: auditEvents.actorId, action: auditEvents.action, subjectType: auditEvents.subjectType, subjectId: auditEvents.subjectId, details: auditEvents.details, createdAt: auditEvents.createdAt })
      .from(auditEvents).orderBy(desc(auditEvents.createdAt)).limit(query.data.limit);
    return { data: rows.map(row => ({
      id:row.id, actorId:row.actorId, action:row.action, subjectType:row.subjectType,
      subjectId:row.subjectId, createdAt:row.createdAt,
      details:row.action==='FEEDBACK_TRIAGE_UPDATED' ? {
        previousStatus:row.details.previousStatus, newStatus:row.details.newStatus,
        previousPriority:row.details.previousPriority, newPriority:row.details.newPriority,
        previousReproduction:row.details.previousReproduction, newReproduction:row.details.newReproduction
      } : undefined
    })) };
  });
  app.post('/v1/moderation/reports/:id/decision', {preHandler:requireRoles('REVIEWER','ADMIN')}, async(request,reply)=>{
    const parsed=z.object({decision:z.enum(['DISMISSED','ACTIONED']),reason:z.string().trim().min(8).max(1000)}).strict().safeParse(request.body);
    if(!parsed.success)return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Report decision is invalid.'}});
    const {id}=request.params as {id:string};
    const [row]=await db.select().from(userReports).where(eq(userReports.id,id)).limit(1);
    if(!row)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Report was not found.'}});
    if(row.status!=='OPEN')return reply.code(409).send({error:{code:'INVALID_STATE',message:'Report was already reviewed.'}});
    await db.transaction(async tx=>{
      await tx.update(userReports).set({status:parsed.data.decision}).where(eq(userReports.id,id));
      await tx.insert(auditEvents).values({actorId:request.principal!.id,action:'USER_REPORT_REVIEWED',subjectType:'user_report',subjectId:id,details:{decision:parsed.data.decision,reason:parsed.data.reason}});
    });
    return {data:{id,status:parsed.data.decision}};
  });
  app.post('/v1/moderation/mods/:modId/versions/:version/quarantine', {preHandler:requireRoles('ADMIN')}, async(request,reply)=>{
    const parsed=z.object({reason:z.string().trim().min(8).max(1000)}).strict().safeParse(request.body);
    if(!parsed.success)return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Quarantine reason is required.'}});
    const {modId,version}=request.params as {modId:string;version:string};
    const [row]=await db.select().from(modVersions).where(and(eq(modVersions.modId,modId),eq(modVersions.version,version),isNotNull(modVersions.publishedAt))).limit(1);
    if(!row)return reply.code(404).send({error:{code:'NOT_FOUND',message:'Published version was not found.'}});
    await db.transaction(async tx=>{
      await tx.update(modVersions).set({quarantinedAt:new Date()}).where(eq(modVersions.id,row.id));
      await tx.insert(auditEvents).values({actorId:request.principal!.id,action:'MOD_VERSION_QUARANTINED',subjectType:'mod_version',subjectId:row.id,details:{reason:parsed.data.reason}});
    });
    return {data:{modId,version,status:'QUARANTINED'}};
  });
}
async function transitionSubmission(id:string,from:AppSubmissionStatus,to:AppSubmissionStatus){assertTransition(from,to);await db.update(submissions).set({status:to,updatedAt:new Date()}).where(eq(submissions.id,id));}
function safeDisplayName(name:string){const base=name.split(/[\\/]/).at(-1)??'upload.doommod';return base.replace(/[^a-zA-Z0-9._-]/g,'_').slice(0,255)||'upload.doommod';}
function safeValidationMessage(error:unknown){const message=error instanceof Error?error.message:'Package validation failed.';return message.length<=240?message:'Package validation failed due to invalid structure.';}
function escapeLike(value:string){return value.replace(/[\\%_]/g,'\\$&');}
function isUniqueViolation(error:unknown){return typeof error==='object'&&error!==null&&'code'in error&&(error as {code?:string}).code==='23505';}
class SubmissionReviewConflict extends Error {}
