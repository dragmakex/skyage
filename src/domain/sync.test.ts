import { parseFlight, parseSyncRequest, parseSyncResponse } from './sync';

const flight = { id: 'legacy-id', date: '2026-02-28', from: 'LHR', to: 'JFK', source: 'manual' as const };

test('flight boundary keeps known fields and validates actual calendar dates', () => {
  expect(parseFlight({ ...flight, owner: 'someone-else' })).toEqual(flight);
  for (const patch of [{ date: '2026-02-30' }, { date: '2026-99-01' }, { from: 'bad' }, { id: '../bad' }, { source: 'unknown' }, { seat: 'x'.repeat(101) }]) {
    expect(() => parseFlight({ ...flight, ...patch })).toThrow();
  }
});

test('sync rejects oversized, duplicate, mismatched and malformed mutations', () => {
  const mutation = { operationId: 'op', id: flight.id, baseRevision: 0, flight };
  expect(parseSyncRequest({ mutations: [mutation] }).mutations).toEqual([mutation]);
  for (const mutations of [[mutation, mutation], Array(101).fill(mutation), [{ ...mutation, flight: { ...flight, id: 'other' } }], [{ ...mutation, baseRevision: -1 }]]) {
    expect(() => parseSyncRequest({ mutations })).toThrow();
  }
});

test('cloud responses require bounded records, unique IDs and boolean outcomes', () => {
  const record = { id: flight.id, revision: 1, flight };
  expect(parseSyncResponse({ records: [record], results: [{ operationId: 'op', accepted: true }] }).records).toEqual([record]);
  expect(() => parseSyncResponse({ records: [record, record], results: [] })).toThrow();
  expect(() => parseSyncResponse({ records: [record], results: [{ operationId: 'op', accepted: 'yes' }] })).toThrow();
});
