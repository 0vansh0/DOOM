import { hash, Algorithm } from '@node-rs/argon2';
import { eq } from 'drizzle-orm';
import { loadConfig } from '../config.js';
import { db, client } from './index.js';
import { users, developerProfiles } from './schema.js';

const config=loadConfig();
try {
  if(!config.ADMIN_SEED_EMAIL||!config.ADMIN_SEED_PASSWORD) throw new Error('Set ADMIN_SEED_EMAIL and ADMIN_SEED_PASSWORD for the one-time seed.');
  const email=config.ADMIN_SEED_EMAIL.toLowerCase();
  const [existing]=await db.select({id:users.id,role:users.role}).from(users).where(eq(users.email,email)).limit(1);
  if(existing) {
    if(existing.role==='ADMIN') console.log('Admin already exists; no change made.');
    else throw new Error('Seed email already belongs to a non-admin account. Refusing role promotion via seed command.');
  } else {
    const passwordHash=await hash(config.ADMIN_SEED_PASSWORD,{algorithm:Algorithm.Argon2id,memoryCost:config.ARGON2_MEMORY_KIB,timeCost:3,parallelism:1});
    await db.transaction(async tx=>{
      const [admin]=await tx.insert(users).values({email,passwordHash,role:'ADMIN'}).returning({id:users.id});
      await tx.insert(developerProfiles).values({userId:admin!.id,displayName:'DOOM Marketplace Administrator'});
    });
    console.log('Created configured administrator account.');
  }
} finally { await client.end(); }
