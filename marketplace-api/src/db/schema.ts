import { pgTable, pgEnum, uuid, text, varchar, timestamp, integer, bigint, jsonb, boolean, index, uniqueIndex } from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

export const userRole = pgEnum('user_role', ['DEVELOPER', 'REVIEWER', 'ADMIN']);
export const submissionStatus = pgEnum('submission_status', ['DRAFT','UPLOADED','VALIDATING','VALIDATION_FAILED','PENDING_REVIEW','APPROVED','REJECTED','PUBLISHED','QUARANTINED']);
export const users = pgTable('users', {
  id: uuid('id').primaryKey().defaultRandom(), email: varchar('email', { length: 320 }).notNull(), passwordHash: text('password_hash').notNull(), role: userRole('role').notNull().default('DEVELOPER'), disabled: boolean('disabled').notNull().default(false), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(), updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow()
}, t => [uniqueIndex('users_email_unique').on(sql`lower(${t.email})`)]);
export const developerProfiles = pgTable('developer_profiles', {
  id: uuid('id').primaryKey().defaultRandom(), userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }), displayName: varchar('display_name', { length: 80 }).notNull(), bio: varchar('bio', { length: 500 }).notNull().default(''), website: varchar('website', { length: 2048 }), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [uniqueIndex('developer_profiles_user_unique').on(t.userId)]);
export const mods = pgTable('mods', {
  id: varchar('id', { length: 64 }).primaryKey(), ownerId: uuid('owner_id').notNull().references(() => users.id), name: varchar('name', { length: 80 }).notNull(), summary: varchar('summary', { length: 500 }).notNull(), category: varchar('category', { length: 40 }).notNull().default('other'), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(), updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('mods_owner_idx').on(t.ownerId), index('mods_name_idx').on(t.name)]);
export const modVersions = pgTable('mod_versions', {
  id: uuid('id').primaryKey().defaultRandom(), modId: varchar('mod_id', { length: 64 }).notNull().references(() => mods.id, { onDelete: 'restrict' }), version: varchar('version', { length: 64 }).notNull(), manifestSchema: integer('manifest_schema').notNull(), definitionVersion: integer('definition_version').notNull(), minAndroidApi: integer('min_android_api').notNull(), capabilities: jsonb('capabilities').$type<string[]>().notNull(), compatibility: jsonb('compatibility').$type<Record<string, unknown>>().notNull().default({}), sha256: varchar('sha256', { length: 64 }).notNull(), objectKey: text('object_key').notNull(), publishedAt: timestamp('published_at', { withTimezone: true }), quarantinedAt: timestamp('quarantined_at', { withTimezone: true }), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [uniqueIndex('mod_versions_mod_version_unique').on(t.modId, t.version), uniqueIndex('mod_versions_object_key_unique').on(t.objectKey), index('mod_versions_published_idx').on(t.publishedAt)]);
export const submissions = pgTable('submissions', {
  id: uuid('id').primaryKey().defaultRandom(), ownerId: uuid('owner_id').notNull().references(() => users.id), modId: varchar('mod_id', { length: 64 }), status: submissionStatus('status').notNull().default('DRAFT'), quarantineKey: text('quarantine_key'), originalName: varchar('original_name', { length: 255 }).notNull().default('upload.doommod'), sha256: varchar('sha256', { length: 64 }), byteSize: bigint('byte_size', { mode: 'number' }), manifest: jsonb('manifest').$type<Record<string, unknown>>(), definition: jsonb('definition').$type<Record<string, unknown>>(), validationSummary: jsonb('validation_summary').$type<Record<string, unknown>>(), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(), updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('submissions_owner_status_idx').on(t.ownerId, t.status), index('submissions_mod_idx').on(t.modId), uniqueIndex('submissions_owner_sha_unique').on(t.ownerId,t.sha256).where(sql`${t.sha256} IS NOT NULL`)]);
export const validationResults = pgTable('validation_results', {
  id: uuid('id').primaryKey().defaultRandom(), submissionId: uuid('submission_id').notNull().references(() => submissions.id, { onDelete: 'cascade' }), validatorVersion: varchar('validator_version', { length: 40 }).notNull(), passed: boolean('passed').notNull(), diagnostics: jsonb('diagnostics').$type<unknown[]>().notNull(), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('validation_submission_idx').on(t.submissionId)]);
export const moderationDecisions = pgTable('moderation_decisions', {
  id: uuid('id').primaryKey().defaultRandom(), submissionId: uuid('submission_id').notNull().references(() => submissions.id), reviewerId: uuid('reviewer_id').notNull().references(() => users.id), decision: varchar('decision', { length: 16 }).notNull(), reason: varchar('reason', { length: 1000 }).notNull(), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('moderation_submission_idx').on(t.submissionId)]);
export const userReports = pgTable('user_reports', {
  id: uuid('id').primaryKey().defaultRandom(), reporterId: uuid('reporter_id').references(() => users.id, { onDelete: 'set null' }), modId: varchar('mod_id', { length: 64 }).notNull().references(() => mods.id), version: varchar('version', { length: 64 }), reason: varchar('reason', { length: 1000 }).notNull(), status: varchar('status', { length: 16 }).notNull().default('OPEN'), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('reports_mod_status_idx').on(t.modId, t.status)]);
export const auditEvents = pgTable('audit_events', {
  id: uuid('id').primaryKey().defaultRandom(), actorId: uuid('actor_id').references(() => users.id, { onDelete: 'set null' }), action: varchar('action', { length: 80 }).notNull(), subjectType: varchar('subject_type', { length: 40 }).notNull(), subjectId: varchar('subject_id', { length: 128 }), details: jsonb('details').$type<Record<string, unknown>>().notNull().default({}), createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow()
}, t => [index('audit_created_idx').on(t.createdAt), index('audit_subject_idx').on(t.subjectType, t.subjectId)]);
export const betaFeedback = pgTable('beta_feedback', {
  id: uuid('id').primaryKey().defaultRandom(), ownerId: uuid('owner_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  digest: text('digest').notNull(), report: jsonb('report').$type<Record<string, string>>().notNull(),
  assigneeId: uuid('assignee_id').references(() => users.id, { onDelete: 'set null' }),
  priority: text('priority').notNull().default('P2'), status: text('status').notNull().default('open'),
  component: text('component').notNull().default(''), reproduction: text('reproduction').notNull().default('unverified'),
  regressionTest: text('regression_test').notNull().default(''), resolution: text('resolution').notNull().default(''),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow()
}, t => [uniqueIndex('beta_feedback_owner_id_digest_key').on(t.ownerId, t.digest), index('beta_feedback_created').on(t.createdAt), index('beta_feedback_status_updated_idx').on(t.status, t.updatedAt)]);
export const uploadQuotas = pgTable('upload_quotas', {
  ownerId: uuid('owner_id').notNull().references(()=>users.id,{onDelete:'cascade'}), quotaDay: varchar('quota_day',{length:10}).notNull(), uploadCount:integer('upload_count').notNull().default(0), byteCount:bigint('byte_count',{mode:'number'}).notNull().default(0), updatedAt:timestamp('updated_at',{withTimezone:true}).notNull().defaultNow()
}, t=>[uniqueIndex('upload_quotas_owner_day_unique').on(t.ownerId,t.quotaDay)]);
