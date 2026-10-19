import type { Flight } from './domain/types';

let mockRows: Flight[] = [];
let mockFailWrite = false;
jest.mock('expo-sqlite/kv-store', () => ({ __esModule: true, default: { getItemSync: () => null, removeItemSync: jest.fn(), setItemSync: jest.fn() } }));
jest.mock('expo-sqlite', () => ({ openDatabaseSync: () => ({ execSync: jest.fn() }) }));
jest.mock('expo-crypto', () => ({ randomUUID: () => 'uuid' }));
jest.mock('react', () => ({ ...jest.requireActual('react'), useSyncExternalStore: (_subscribe: unknown, snapshot: () => unknown) => snapshot() }));
jest.mock('./storage/flights', () => ({
  ownerFor: () => 'guest',
  FlightStorage: jest.fn().mockImplementation(() => ({
    migrate: jest.fn(), account: () => null,
    flights: () => mockRows.map((f) => ({ ...f })), conflicts: () => [], pendingCount: () => 0,
    apply: jest.fn(),
    save: (_owner: string, list: Flight[]) => { if (mockFailWrite) throw new Error('Disk full'); mockRows = list; },
  })),
}));

const { addFlights, applySync, useFlights } = require('./store') as typeof import('./store');

test('unchanged cloud polls preserve the flight snapshot and failed disk writes do not publish unsaved flights', () => {
  const empty = useFlights();
  applySync('guest', [], { records: [], results: [] });
  expect(useFlights()).toBe(empty);
  const flight: Flight = { id: 'one', date: '2026-01-01', from: 'LHR', to: 'JFK', source: 'manual' };
  addFlights([flight]);
  const saved = useFlights();
  expect(saved).toEqual([flight]);
  applySync('guest', [], { records: [], results: [] });
  expect(useFlights()).toBe(saved);
  mockFailWrite = true;
  expect(() => addFlights([{ ...flight, id: 'two' }])).toThrow('Disk full');
  expect(useFlights()).toBe(saved);
});
