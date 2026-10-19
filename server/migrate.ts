import { getMigrations } from 'better-auth/db/migration';
import { configuredApp } from './config';

const app = configuredApp();
try {
  const migration = await getMigrations(app.auth.options);
  await migration.runMigrations();
  console.info('Authentication schema is up to date.');
} finally { app.db.close(); }
