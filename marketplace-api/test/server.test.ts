import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { buildServer } from '../src/server.js';
import { MemoryArtifactStorage } from '../src/storage/s3.js';
import type { FastifyInstance } from 'fastify';

describe('Fastify marketplace boundary',()=>{
  let app:FastifyInstance;
  beforeAll(async()=>{app=await buildServer({storage:new MemoryArtifactStorage()});await app.ready();});
  afterAll(async()=>{await app.close();});
  it('serves the checked-in OpenAPI contract',async()=>{
    const result=await app.inject({method:'GET',url:'/openapi.yaml'});
    expect(result.statusCode).toBe(200);expect(result.headers['content-type']).toContain('application/yaml');
    expect(result.body).toContain('/v1/catalog/mods');
  });
  it('rejects protected routes without a bearer token',async()=>{
    const result=await app.inject({method:'GET',url:'/v1/submissions'});
    expect(result.statusCode).toBe(401);expect(result.json().error.code).toBe('UNAUTHENTICATED');
  });
  it('rejects malformed registration without touching account storage',async()=>{
    const result=await app.inject({method:'POST',url:'/v1/auth/register',payload:{email:'not-an-email',password:'short',role:'ADMIN'}});
    expect(result.statusCode).toBe(400);expect(result.json().error.code).toBe('INVALID_INPUT');
  });
});
