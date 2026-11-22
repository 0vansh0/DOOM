import postgres from 'postgres';
import { drizzle } from 'drizzle-orm/postgres-js';
import { loadConfig } from '../config.js';
import * as schema from './schema.js';
const config = loadConfig();
export const client = postgres(config.DATABASE_URL, {
  max: config.NODE_ENV === 'test' ? 4 : config.DB_POOL_SIZE,
  connect_timeout: config.DB_CONNECT_TIMEOUT_SECONDS,
  idle_timeout: config.DB_IDLE_TIMEOUT_SECONDS,
  queue_timeout: config.DB_QUEUE_TIMEOUT_SECONDS,
  query_timeout: config.DB_QUERY_TIMEOUT_SECONDS,
  ssl: config.DATABASE_SSL_MODE,
  connection: {
    application_name: 'doom-marketplace-api',
    statement_timeout: config.DB_STATEMENT_TIMEOUT_MS,
    lock_timeout: config.DB_LOCK_TIMEOUT_MS,
    idle_in_transaction_session_timeout: config.DB_IDLE_TRANSACTION_TIMEOUT_MS,
  },
});
export const db = drizzle(client, { schema });
