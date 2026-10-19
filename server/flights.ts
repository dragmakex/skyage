import type { CloudFlight, SyncRequest, SyncResponse } from '../src/domain/sync';
import type { Sql } from '../src/storage/sql';

type Row = { id: string; payload: string | null; revision: number };

export class CloudFlights {
  constructor(private db: Sql) {
    db.exec(`
      CREATE TABLE IF NOT EXISTS cloud_flights (
        user_id TEXT NOT NULL REFERENCES user(id) ON DELETE CASCADE,
        id TEXT NOT NULL, payload TEXT, revision INTEGER NOT NULL CHECK(revision > 0),
        PRIMARY KEY(user_id,id)
      );
      CREATE TABLE IF NOT EXISTS sync_receipts (
        user_id TEXT NOT NULL REFERENCES user(id) ON DELETE CASCADE,
        operation_id TEXT NOT NULL, mutation TEXT NOT NULL, accepted INTEGER NOT NULL,
        PRIMARY KEY(user_id,operation_id)
      );
    `);
  }

  sync(userId: string, request: SyncRequest): SyncResponse {
    return this.db.transaction(() => {
      const results = request.mutations.map((mutation) => {
        const encoded = JSON.stringify(mutation);
        const receipt = this.db.all<{ mutation: string; accepted: number }>(
          'SELECT mutation,accepted FROM sync_receipts WHERE user_id=? AND operation_id=?', userId, mutation.operationId)[0];
        if (receipt) {
          if (receipt.mutation !== encoded) throw new Error('Operation ID was reused with different data.');
          return { operationId: mutation.operationId, accepted: receipt.accepted === 1 };
        }
        const row = this.db.all<Row>('SELECT id,payload,revision FROM cloud_flights WHERE user_id=? AND id=?', userId, mutation.id)[0];
        const accepted = (row?.revision ?? 0) === mutation.baseRevision;
        if (accepted) {
          this.db.run('INSERT OR REPLACE INTO cloud_flights(user_id,id,payload,revision) VALUES (?,?,?,?)',
            userId, mutation.id, mutation.flight === null ? null : JSON.stringify(mutation.flight), (row?.revision ?? 0) + 1);
        }
        this.db.run('INSERT INTO sync_receipts(user_id,operation_id,mutation,accepted) VALUES (?,?,?,?)', userId, mutation.operationId, encoded, accepted ? 1 : 0);
        return { operationId: mutation.operationId, accepted };
      });
      // shortcut: full-account snapshots suit personal flight histories; paginate when measured payloads require it.
      const records = this.db.all<Row>('SELECT id,payload,revision FROM cloud_flights WHERE user_id=?', userId)
        .map((row): CloudFlight => ({ id: row.id, revision: row.revision, flight: row.payload === null ? null : JSON.parse(row.payload) }));
      for (const mutation of request.mutations) {
        if (!records.some((r) => r.id === mutation.id)) records.push({ id: mutation.id, revision: 0, flight: null });
      }
      return { records, results };
    });
  }
}
