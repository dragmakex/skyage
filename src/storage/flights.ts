import { parseFlight, type CloudFlight, type Mutation, type SyncResponse } from '../domain/sync';
import type { Flight } from '../domain/types';
import type { Sql } from './sql';

export type Account = { id: string; email: string };
export type Conflict = { id: string; local: Flight | null; cloud: CloudFlight };
type Row = { id: string; payload: string | null; revision: number; pending: string | null; conflict: string | null };
export const ownerFor = (account: Account | null) => account ? `account:${account.id}` : 'guest';
const decode = (row: Row): Flight | null => row.payload === null ? null : parseFlight(JSON.parse(row.payload));

export class FlightStorage {
  constructor(private db: Sql, private id: () => string) {
    db.exec(`
      CREATE TABLE IF NOT EXISTS local_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS flight_records (
        owner TEXT NOT NULL, id TEXT NOT NULL, payload TEXT, revision INTEGER NOT NULL DEFAULT 0,
        pending TEXT, conflict TEXT, PRIMARY KEY(owner, id)
      );
    `);
  }

  migrate(legacy: string | null) {
    if (this.db.all('SELECT 1 FROM local_meta WHERE key = ?', 'flights_migrated').length) return;
    const data: unknown = JSON.parse(legacy ?? '[]');
    if (!Array.isArray(data)) throw new Error('Could not migrate flights. Original data has been preserved.');
    const flights = data.map(parseFlight);
    this.db.transaction(() => {
      for (const f of flights) this.db.run('INSERT INTO flight_records(owner,id,payload) VALUES (?,?,?)', 'guest', f.id, JSON.stringify(f));
      this.db.run('INSERT INTO local_meta(key,value) VALUES (?,?)', 'flights_migrated', '1');
    });
    // The caller can retire the old KV blob only after this transaction succeeds.
  }

  account(): Account | null {
    const row = this.db.all<{ value: string }>('SELECT value FROM local_meta WHERE key = ?', 'account')[0];
    return row ? JSON.parse(row.value) : null;
  }

  setAccount(account: Account | null) {
    this.db.run('INSERT OR REPLACE INTO local_meta(key,value) VALUES (?,?)', 'account', JSON.stringify(account));
  }

  private rows(owner: string) { return this.db.all<Row>('SELECT * FROM flight_records WHERE owner = ?', owner); }

  flights(owner: string): Flight[] {
    return this.rows(owner).flatMap((row) => { const f = decode(row); return f ? [f] : []; })
      .sort((a, b) => b.date.localeCompare(a.date));
  }

  save(owner: string, flights: Flight[]) {
    const checked = flights.map(parseFlight);
    this.db.transaction(() => {
      for (const f of checked) {
        this.db.run(`INSERT INTO flight_records(owner,id,payload,pending) VALUES (?,?,?,?)
          ON CONFLICT(owner,id) DO UPDATE SET payload=excluded.payload,pending=excluded.pending`,
        owner, f.id, JSON.stringify(f), owner === 'guest' ? null : this.id());
      }
    });
  }

  remove(owner: string, id: string) {
    if (owner === 'guest') this.db.run('DELETE FROM flight_records WHERE owner=? AND id=?', owner, id);
    else this.db.run('UPDATE flight_records SET payload=NULL,pending=? WHERE owner=? AND id=?', this.id(), owner, id);
  }

  pendingCount(owner: string) { return this.rows(owner).filter((r) => r.pending).length; }

  pending(owner: string): Mutation[] {
    return this.rows(owner).filter((r) => r.pending && !r.conflict).slice(0, 100)
      .map((r) => ({ id: r.id, operationId: r.pending!, baseRevision: r.revision, flight: decode(r) }));
  }

  conflicts(owner: string): Conflict[] {
    return this.rows(owner).filter((r) => r.conflict).map((r) => ({ id: r.id, local: decode(r), cloud: JSON.parse(r.conflict!) }));
  }

  // Import is explicit. Moving guest rows in the same transaction prevents repeated uploads on later logins.
  uploadGuest(owner: string) {
    if (owner === 'guest') return;
    this.db.transaction(() => {
      const existing = this.flights(owner);
      const fresh = this.flights('guest').filter((f) => !existing.some((e) =>
        e.date === f.date && e.from === f.from && e.to === f.to && (e.number ?? '') === (f.number ?? '')));
      for (const f of fresh) {
        // An ID collision with a different cloud flight must never overwrite it.
        const collision = this.db.all('SELECT 1 FROM flight_records WHERE owner=? AND id=?', owner, f.id).length;
        const moved = { ...f, id: collision ? this.id() : f.id };
        this.db.run('INSERT INTO flight_records(owner,id,payload,pending) VALUES (?,?,?,?)', owner, moved.id, JSON.stringify(moved), this.id());
      }
      this.db.run('DELETE FROM flight_records WHERE owner=?', 'guest');
    });
  }

  apply(owner: string, sent: Mutation[], response: SyncResponse) {
    const outcomes = new Map(response.results.map((r) => [r.operationId, r.accepted]));
    const cloud = new Map(response.records.map((r) => [r.id, r]));
    if (outcomes.size !== sent.length || sent.some((m) => !outcomes.has(m.operationId) || !cloud.has(m.id))) throw new Error('Incomplete sync response.');
    this.db.transaction(() => {
      for (const record of response.records) {
        const row = this.db.all<Row>('SELECT * FROM flight_records WHERE owner=? AND id=?', owner, record.id)[0];
        const mutation = sent.find((m) => m.id === record.id);
        if (row?.pending) {
          if (mutation && outcomes.get(mutation.operationId)) {
            if (row.pending !== mutation.operationId) {
              if (record.revision === mutation.baseRevision + 1) this.db.run('UPDATE flight_records SET revision=? WHERE owner=? AND id=?', record.revision, owner, record.id);
              else this.db.run('UPDATE flight_records SET conflict=? WHERE owner=? AND id=?', JSON.stringify(record), owner, record.id);
              continue; // Preserve newer local edits, including after an acknowledged retry.
            }
          } else {
            if (record.revision !== row.revision || mutation) this.db.run('UPDATE flight_records SET conflict=? WHERE owner=? AND id=?', JSON.stringify(record), owner, record.id);
            continue;
          }
        }
        this.db.run(`INSERT OR REPLACE INTO flight_records(owner,id,payload,revision,pending,conflict) VALUES (?,?,?,?,NULL,NULL)`,
          owner, record.id, record.flight === null ? null : JSON.stringify(record.flight), record.revision);
      }
    });
  }

  resolve(owner: string, id: string, choice: 'local' | 'cloud') {
    const row = this.db.all<Row>('SELECT * FROM flight_records WHERE owner=? AND id=?', owner, id)[0];
    if (!row?.conflict) return;
    const cloud: CloudFlight = JSON.parse(row.conflict);
    this.db.run('UPDATE flight_records SET payload=?,revision=?,pending=?,conflict=NULL WHERE owner=? AND id=?',
      choice === 'local' ? row.payload : cloud.flight === null ? null : JSON.stringify(cloud.flight),
      cloud.revision, choice === 'local' ? this.id() : null, owner, id);
  }

  forgetAccount(owner: string) {
    this.db.transaction(() => {
      this.db.run('DELETE FROM flight_records WHERE owner=?', owner);
      this.setAccount(null);
    });
  }
}
