import { Database } from 'bun:sqlite';
import { existsSync } from 'node:fs';

const source = process.env.DATABASE_PATH ?? './server/data/skyage.sqlite';
const target = process.argv[2];
if (!existsSync(source) || !target || existsSync(target)) throw new Error('Usage: bun run server:backup /existing-backup-directory/new-snapshot.sqlite. Source must exist; destination must not exist.');
const db = new Database(source, { readonly: true });
try {
  db.query('VACUUM INTO ?').run(target);
  console.info('Consistent SQLite snapshot created. Copy it to off-server storage.');
} finally { db.close(); }
