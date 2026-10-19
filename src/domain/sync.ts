import type { Flight } from './types';

export type CloudFlight = { id: string; revision: number; flight: Flight | null };
export type Mutation = { operationId: string; id: string; baseRevision: number; flight: Flight | null };
export type SyncRequest = { mutations: Mutation[] };
export type SyncResponse = { records: CloudFlight[]; results: { operationId: string; accepted: boolean }[] };

const object = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
export const validId = (v: unknown): v is string => typeof v === 'string' && /^[A-Za-z0-9_-]{1,100}$/.test(v);
const revision = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;

// Only known, bounded fields cross the storage/API boundary.
export function parseFlight(value: unknown): Flight {
  if (!object(value) || !validId(value.id) || typeof value.date !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value.date) ||
    new Date(`${value.date}T00:00:00Z`).toISOString().slice(0, 10) !== value.date ||
    typeof value.from !== 'string' || !/^[A-Z]{3}$/.test(value.from) ||
    typeof value.to !== 'string' || !/^[A-Z]{3}$/.test(value.to) ||
    !['scan', 'lookup', 'manual', 'import'].includes(value.source as string)) throw new Error('Invalid flight.');
  const flight: Flight = { id: value.id, date: value.date, from: value.from, to: value.to, source: value.source as Flight['source'] };
  for (const key of ['airline', 'number', 'seat', 'depTime', 'arrTime'] as const) {
    const field = value[key];
    if (field !== undefined) {
      if (typeof field !== 'string' || field.length > 100) throw new Error('Invalid flight field.');
      flight[key] = field;
    }
  }
  return flight;
}

function parseRecord(value: unknown): CloudFlight {
  if (!object(value) || !validId(value.id) || !revision(value.revision)) throw new Error('Invalid cloud record.');
  const flight = value.flight === null ? null : parseFlight(value.flight);
  if (flight && flight.id !== value.id) throw new Error('Flight ID mismatch.');
  return { id: value.id, revision: value.revision, flight };
}

export function parseSyncRequest(value: unknown): SyncRequest {
  if (!object(value) || !Array.isArray(value.mutations) || value.mutations.length > 100) throw new Error('Invalid sync batch.');
  const operations = new Set<string>();
  const ids = new Set<string>();
  const mutations = value.mutations.map((v: unknown): Mutation => {
    if (!object(v) || !validId(v.operationId) || !revision(v.baseRevision) || operations.has(v.operationId)) throw new Error('Invalid mutation.');
    const record = parseRecord({ ...v, revision: v.baseRevision });
    if (ids.has(record.id)) throw new Error('Duplicate flight in batch.');
    operations.add(v.operationId); ids.add(record.id);
    return { operationId: v.operationId, id: record.id, baseRevision: v.baseRevision, flight: record.flight };
  });
  return { mutations };
}

export function parseSyncResponse(value: unknown): SyncResponse {
  if (!object(value) || !Array.isArray(value.records) || !Array.isArray(value.results)) throw new Error('Invalid sync response.');
  const records = value.records.map(parseRecord);
  if (new Set(records.map((r) => r.id)).size !== records.length) throw new Error('Duplicate cloud record.');
  const results = value.results.map((v: unknown) => {
    if (!object(v) || !validId(v.operationId) || typeof v.accepted !== 'boolean') throw new Error('Invalid sync result.');
    return { operationId: v.operationId, accepted: v.accepted };
  });
  if (new Set(results.map((r) => r.operationId)).size !== results.length) throw new Error('Duplicate sync outcome.');
  return { records, results };
}
