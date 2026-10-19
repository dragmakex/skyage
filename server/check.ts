import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Database } from 'bun:sqlite';
import { getMigrations } from 'better-auth/db/migration';
import { FlightStorage, ownerFor } from '../src/storage/flights';
import type { Flight } from '../src/domain/types';
import { createApp } from './app';
import { openDatabase, sql } from './database';

const f: Flight = { id: 'legacy', date: '2025-01-01', from: 'LHR', to: 'JFK', source: 'manual' };
let sequence = 0;
function local() {
  const db = openDatabase(':memory:');
  return { db, store: new FlightStorage(sql(db), () => `op-${++sequence}`) };
}

// Actual SQLite transactions, including rollback and migration recovery.
{
  const { db, store } = local();
  assert.throws(() => store.migrate(JSON.stringify([f, { bad: true }])));
  assert.deepEqual(store.flights('guest'), []);
  assert.equal(db.query('SELECT * FROM local_meta').all().length, 0);
  assert.throws(() => store.migrate(JSON.stringify([f, f])), 'duplicate legacy IDs must not silently discard a flight');
  assert.deepEqual(store.flights('guest'), []);
  store.migrate(JSON.stringify([f]));
  store.migrate(JSON.stringify([{ ...f, id: 'should-not-import-again' }]));
  assert.deepEqual(store.flights('guest'), [f]);
  store.setAccount({ id: 'alice', email: 'alice@example.com' });
  assert.equal(store.account()?.id, 'alice');
  const owner = ownerFor(store.account());
  store.uploadGuest(owner);
  assert.deepEqual(store.flights('guest'), []);
  assert.deepEqual(store.flights(owner), [f]);
  const sent = store.pending(owner);
  store.remove(owner, f.id); // User deletes during an in-flight create.
  store.apply(owner, sent, { records: [{ id: f.id, revision: 1, flight: f }], results: [{ operationId: sent[0].operationId, accepted: true }] });
  assert.deepEqual(store.flights(owner), []);
  assert.equal(store.pending(owner)[0].baseRevision, 1);
  assert.equal(store.pending(owner)[0].flight, null);
  const deletion = store.pending(owner);
  store.apply(owner, deletion, { records: [{ id: f.id, revision: 2, flight: { ...f, seat: '2A' } }], results: [{ operationId: deletion[0].operationId, accepted: false }] });
  assert.equal(store.conflicts(owner).length, 1);
  assert.equal(store.pending(owner).length, 0);
  store.resolve(owner, f.id, 'cloud');
  assert.equal(store.flights(owner)[0].seat, '2A');
  assert.equal(store.pendingCount(owner), 0);
  assert.deepEqual(store.flights('account:bob'), []);
  store.remove(owner, f.id);
  assert.throws(() => store.apply(owner, store.pending(owner), { records: [], results: [] }));
  assert.equal(store.pendingCount(owner), 1);
  store.forgetAccount(owner);
  assert.equal(store.account(), null);
  assert.deepEqual(store.flights(owner), []);
  db.close();
}

// Mid-transaction failures must leave both the marker and imported rows untouched.
{
  const db = openDatabase(':memory:');
  const adapter = sql(db);
  const store = new FlightStorage({ ...adapter, run(query, ...values) {
    if (query.startsWith('INSERT INTO flight_records') && values[1] === 'second') throw new Error('Simulated disk failure');
    adapter.run(query, ...values);
  } }, () => 'unused');
  assert.throws(() => store.migrate(JSON.stringify([f, { ...f, id: 'second' }])));
  assert.deepEqual(store.flights('guest'), []);
  assert.equal(db.query('SELECT * FROM local_meta').all().length, 0);
  db.close();
}

// An acknowledged request whose response was lost must not silently rebase over a third-party edit.
{
  const { db, store } = local();
  const owner = 'account:alice';
  store.save(owner, [f]);
  const sent = store.pending(owner);
  store.save(owner, [{ ...f, seat: '1A' }]);
  store.apply(owner, sent, { records: [{ id: f.id, revision: 2, flight: { ...f, seat: '2A' } }], results: [{ operationId: sent[0].operationId, accepted: true }] });
  assert.equal(store.conflicts(owner).length, 1);
  assert.equal(store.flights(owner)[0].seat, '1A');
  store.resolve(owner, f.id, 'local');
  assert.equal(store.pending(owner)[0].baseRevision, 2);
  db.close();
}

const directory = mkdtempSync(join(tmpdir(), 'skyage-check-'));
const db = openDatabase(join(directory, 'api.sqlite'));
const codes = new Map<string, string>();
const app = createApp(db, {
  baseURL: 'http://localhost:3000',
  secret: 'test-secret-only-for-automated-checks-at-least-32-characters',
  sendCode: async (email, code) => { codes.set(email, code); },
});
try {
  const migration = await getMigrations(app.auth.options);
  assert.ok(migration.toBeCreated.length > 0, 'fresh databases require explicit migration');
  await migration.runMigrations();
  const ready = await getMigrations(app.auth.options);
  assert.deepEqual([ready.toBeCreated, ready.toBeAdded, ready.toBeAddedIndexes, ready.schemaProblems, ready.unsafeChanges], [[], [], [], [], []]);
  let requestCount = 0;
  async function call(path: string, body: unknown, cookie = '', user = '', ip = `192.0.2.${++requestCount}`) {
    return app.handle(new Request(`http://localhost:3000${path}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'expo-origin': 'skyage://', Cookie: cookie, 'x-skyage-account': user },
      body: JSON.stringify(body),
    }), ip);
  }
  async function login(email: string) {
    const send = await call('/api/auth/email-otp/send-verification-otp', { email, type: 'sign-in' });
    assert.equal(send.status, 200, await send.text());
    const code = codes.get(email);
    assert.ok(code);
    const verification = db.query('SELECT value FROM verification').all() as { value: string }[];
    assert.ok(verification.every((row) => !row.value.includes(code)), 'OTP must not be stored in plaintext');
    const response = await call('/api/auth/sign-in/email-otp', { email, otp: code });
    const data = await response.json() as { user: { id: string } };
    assert.equal(response.status, 200, JSON.stringify(data));
    const cookie = response.headers.getSetCookie().map((entry) => entry.split(';')[0]).join('; ');
    assert.ok(cookie);
    const replay = await call('/api/auth/sign-in/email-otp', { email, otp: code });
    assert.notEqual(replay.status, 200, 'OTP must be single-use');
    return { id: data.user.id, cookie };
  }
  const alice = await login('alice@example.com');
  const bob = await login('bob@example.com');
  const mutation = { operationId: 'create-1', id: f.id, baseRevision: 0, flight: f };
  assert.equal((await call('/api/sync', { mutations: [mutation] })).status, 401);
  assert.equal((await call('/api/sync', { mutations: [mutation] }, alice.cookie, bob.id)).status, 401);
  const sync = async (user: typeof alice, mutations: unknown[]) => {
    const response = await call('/api/sync', { mutations }, user.cookie, user.id);
    assert.equal(response.status, 200, response.status === 200 ? '' : await response.text());
    return response.json() as Promise<import('../src/domain/sync').SyncResponse>;
  };
  const created = await sync(alice, [mutation]);
  assert.equal(created.records[0].revision, 1);
  assert.deepEqual(await sync(alice, [mutation]), created, 'lost-response retry is idempotent');
  assert.deepEqual((await sync(bob, [])).records, [], 'users cannot read other users’ flights');
  const conflict = await sync(alice, [{ ...mutation, operationId: 'stale-edit', flight: { ...f, seat: '2A' } }]);
  assert.equal(conflict.results[0].accepted, false);
  assert.equal(conflict.records[0].flight?.seat, undefined);
  assert.equal((await call('/api/sync', { mutations: [{ ...mutation, flight: { ...f, seat: 'changed' } }] }, alice.cookie, alice.id)).status, 409);
  const rolledBack = await call('/api/sync', { mutations: [
    { operationId: 'rollback-first', id: 'rollback-flight', baseRevision: 0, flight: { ...f, id: 'rollback-flight' } },
    { ...mutation, flight: { ...f, seat: 'reused-operation' } },
  ] }, alice.cookie, alice.id);
  assert.equal(rolledBack.status, 409);
  assert.ok(!(await sync(alice, [])).records.some((r) => r.id === 'rollback-flight'), 'whole batch rolls back on failure');
  const missing = await sync(alice, [{ ...mutation, id: 'missing', flight: null, baseRevision: 4, operationId: 'missing-edit' }]);
  assert.equal(missing.results[0].accepted, false);
  assert.deepEqual(missing.records.find((r) => r.id === 'missing'), { id: 'missing', revision: 0, flight: null });
  assert.equal((await call('/api/sync', { mutations: [{ ...mutation, operationId: 'bad', flight: { ...f, date: 'not-date' } }] }, alice.cookie, alice.id)).status, 400);
  const deleted = await sync(alice, [{ ...mutation, operationId: 'delete-1', baseRevision: 1, flight: null }]);
  assert.equal(deleted.records[0].flight, null);
  assert.equal(deleted.records[0].revision, 2);
  assert.equal((await sync(alice, [{ ...mutation, operationId: 'offline-resurrection' }])).results[0].accepted, false);

  // Two independent phones converge and a real file-backed client survives reopening.
  const phonePath = join(directory, 'phone.sqlite');
  const phoneDb = openDatabase(phonePath);
  const phone = new FlightStorage(sql(phoneDb), () => `phone-${++sequence}`);
  phone.migrate(null);
  const owner = `account:${bob.id}`;
  phone.save(owner, [f]);
  const queued = phone.pending(owner);
  phone.apply(owner, queued, await sync(bob, queued));
  phoneDb.close();
  const reopened = openDatabase(phonePath);
  const restored = new FlightStorage(sql(reopened), () => `phone-${++sequence}`);
  assert.deepEqual(restored.flights(owner), [f]);
  assert.equal(restored.pendingCount(owner), 0);
  const second = local();
  second.store.apply(owner, [], await sync(bob, []));
  assert.deepEqual(second.store.flights(owner), restored.flights(owner));
  second.store.remove(owner, f.id);
  const remove = second.store.pending(owner);
  second.store.apply(owner, remove, await sync(bob, remove));
  restored.apply(owner, [], await sync(bob, []));
  assert.deepEqual(restored.flights(owner), []);
  reopened.close(); second.db.close();

  const attemptsEmail = 'attempts@example.com';
  assert.equal((await call('/api/auth/email-otp/send-verification-otp', { email: attemptsEmail, type: 'sign-in' })).status, 200);
  const correct = codes.get(attemptsEmail)!;
  for (let i = 0; i < 3; i++) {
    assert.notEqual((await call('/api/auth/sign-in/email-otp', { email: attemptsEmail, otp: correct === '000000' ? '111111' : '000000' })).status, 200);
  }
  assert.notEqual((await call('/api/auth/sign-in/email-otp', { email: attemptsEmail, otp: correct })).status, 200, 'correct code cannot bypass exhausted attempts');
  const expiredEmail = 'expired@example.com';
  assert.equal((await call('/api/auth/email-otp/send-verification-otp', { email: expiredEmail, type: 'sign-in' })).status, 200);
  db.query('UPDATE verification SET expiresAt=0').run();
  assert.notEqual((await call('/api/auth/sign-in/email-otp', { email: expiredEmail, otp: codes.get(expiredEmail) })).status, 200, 'expired OTP is rejected');

  for (let i = 0; i < 4; i++) {
    const response = await call('/api/auth/email-otp/send-verification-otp', { email: 'limit@example.com', type: 'sign-in' }, '', '', '198.51.100.1');
    assert.equal(response.status, i < 3 ? 200 : 429, 'OTP requests are rate limited');
  }
  const signout = await call('/api/auth/sign-out', {}, bob.cookie);
  assert.equal(signout.status, 200);
  assert.equal((await call('/api/sync', { mutations: [] }, bob.cookie, bob.id)).status, 401);
  const removeAccount = await call('/api/auth/delete-user', {}, alice.cookie);
  assert.equal(removeAccount.status, 200, await removeAccount.text());
  assert.equal(db.query('SELECT * FROM cloud_flights WHERE user_id=?').all(alice.id).length, 0);
  assert.equal(db.query('SELECT * FROM sync_receipts WHERE user_id=?').all(alice.id).length, 0);
  assert.equal((await call('/api/sync', { mutations: [] }, alice.cookie, alice.id)).status, 401);

  const backup = join(directory, 'backup.sqlite');
  db.query('VACUUM INTO ?').run(backup);
  const restoredDb = new Database(backup, { readonly: true });
  assert.deepEqual(restoredDb.query('PRAGMA integrity_check').get(), { integrity_check: 'ok' });
  assert.equal(restoredDb.query('SELECT * FROM user').all().length, 1);
  restoredDb.close();
  console.info('Server and local SQLite checks passed: migration, offline queue, retries, conflicts, restart, two phones, ownership, OTP, revocation, deletion and backup restore.');
} finally {
  db.close(); rmSync(directory, { recursive: true, force: true });
}
