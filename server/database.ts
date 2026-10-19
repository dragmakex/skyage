import { Database } from 'bun:sqlite';
import type { Sql } from '../src/storage/sql';

export function sql(db: Database): Sql {
  return {
    exec: (query) => { db.exec(query); },
    run: (query, ...values) => { db.query(query).run(...values); },
    all: <T>(query: string, ...values: (string | number | null)[]) => db.query(query).all(...values) as T[],
    transaction: <T>(work: () => T) => db.transaction(work)(),
  };
}

export function openDatabase(path: string) {
  const db = new Database(path, { create: true });
  db.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;');
  return db;
}
