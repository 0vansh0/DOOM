import { hash, verify, Algorithm } from '@node-rs/argon2';
import { eq, sql } from 'drizzle-orm';
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { db } from '../db/index.js';
import { users, developerProfiles, auditEvents } from '../db/schema.js';
import { loadConfig } from '../config.js';
import type { Principal, UserRole } from '../domain/types.js';

const config = loadConfig();
const registerBody = z.object({ email: z.string().email().max(320), password: z.string().min(12).max(128), displayName: z.string().trim().min(2).max(80) }).strict();
const loginBody = z.object({ email: z.string().email().max(320), password: z.string().min(1).max(128) }).strict();
const options = { algorithm: Algorithm.Argon2id, memoryCost: config.ARGON2_MEMORY_KIB, timeCost: 3, parallelism: 1 };
declare module 'fastify' { interface FastifyRequest { principal?: Principal } }

export async function registerAuth(app: FastifyInstance) {
  app.post('/v1/auth/register', {config:{rateLimit:{max:config.AUTH_REGISTER_RATE_LIMIT,timeWindow:config.AUTH_RATE_LIMIT_WINDOW_MS}}}, async (request, reply) => {
    const parsed = registerBody.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: { code: 'INVALID_INPUT', message: 'Registration fields are invalid.' } });
    const email = parsed.data.email.trim().toLowerCase();
    const passwordHash = await hash(parsed.data.password, options);
    try {
      const user = await db.transaction(async tx => {
        const [created] = await tx.insert(users).values({ email, passwordHash, role: 'DEVELOPER' }).returning({ id: users.id, email: users.email, role: users.role });
        await tx.insert(developerProfiles).values({ userId: created!.id, displayName: parsed.data.displayName });
        await tx.insert(auditEvents).values({actorId:null,action:'USER_REGISTERED',subjectType:'user',subjectId:created!.id,details:{}});
        return created!;
      });
      const token = app.jwt.sign({ sub: user.id }, { expiresIn: config.ACCESS_TOKEN_TTL_SECONDS });
      return reply.code(201).send({ data: { accessToken: token, tokenType: 'Bearer', expiresIn: config.ACCESS_TOKEN_TTL_SECONDS, user: { id: user.id, email: user.email, role: user.role } } });
    } catch (error) {
      if (isUnique(error)) return reply.code(409).send({ error: { code: 'EMAIL_EXISTS', message: 'An account already exists for this email.' } });
      throw error;
    }
  });
  app.post('/v1/auth/login', {config:{rateLimit:{max:config.AUTH_LOGIN_RATE_LIMIT,timeWindow:config.AUTH_RATE_LIMIT_WINDOW_MS}}}, async (request, reply) => {
    const parsed = loginBody.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: { code: 'INVALID_INPUT', message: 'Login fields are invalid.' } });
    const email = parsed.data.email.trim().toLowerCase();
    const [user] = await db.select().from(users).where(sql`lower(${users.email}) = ${email}`).limit(1);
    if (!user || user.disabled || !(await verify(user.passwordHash, parsed.data.password)))
      return reply.code(401).send({ error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.' } });
    const token = app.jwt.sign({ sub: user.id }, { expiresIn: config.ACCESS_TOKEN_TTL_SECONDS });
    await audit(user.id, 'USER_LOGIN', 'user', user.id);
    return { data: { accessToken: token, tokenType: 'Bearer', expiresIn: config.ACCESS_TOKEN_TTL_SECONDS, user: { id: user.id, email: user.email, role: user.role } } };
  });
}

export async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  try {
    await request.jwtVerify();
    const id = String((request.user as { sub?: string }).sub ?? '');
    const [user] = await db.select({ id: users.id, email: users.email, role: users.role, disabled: users.disabled }).from(users).where(eq(users.id, id)).limit(1);
    if (!user || user.disabled) { await reply.code(401).send({ error: { code: 'UNAUTHENTICATED', message: 'Authentication is required.' } }); return; }
    request.principal = { id: user.id, email: user.email, role: user.role as UserRole };
  } catch { await reply.code(401).send({ error: { code: 'UNAUTHENTICATED', message: 'Authentication is required.' } }); }
}
export function requireRoles(...roles: UserRole[]) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    await authenticate(request, reply);
    if (!reply.sent && (!request.principal || !roles.includes(request.principal.role)))
      await reply.code(403).send({ error: { code: 'FORBIDDEN', message: 'This operation requires a different account role.' } });
  };
}
export async function audit(actorId: string | null, action: string, subjectType: string, subjectId?: string, details: Record<string, unknown> = {}) {
  await db.insert(auditEvents).values({ actorId, action, subjectType, subjectId, details });
}
function isUnique(error: unknown) { return typeof error === 'object' && error !== null && 'code' in error && (error as { code: string }).code === '23505'; }
