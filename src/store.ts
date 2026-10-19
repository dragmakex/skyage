import Storage from 'expo-sqlite/kv-store';
import { openDatabaseSync } from 'expo-sqlite';
import { randomUUID } from 'expo-crypto';
import { useSyncExternalStore } from 'react';
import type { Flight } from './domain/types';
import type { Mutation, SyncResponse } from './domain/sync';
import { FlightStorage, ownerFor, type Account } from './storage/flights';
import type { Sql } from './storage/sql';

export type Settings = { theme: 'system' | 'light' | 'dark'; units: 'km' | 'mi' };
const SETTINGS = 'settings';
const db = openDatabaseSync('skyage.sqlite');
db.execSync('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;');
const sql: Sql = {
  exec: (query) => db.execSync(query),
  run: (query, ...values) => { db.runSync(query, ...values); },
  all: <T>(query: string, ...values: (string | number | null)[]) => db.getAllSync<T>(query, ...values),
  transaction: <T>(work: () => T): T => {
    let result!: T;
    db.withTransactionSync(() => { result = work(); });
    return result;
  },
};
const storage = new FlightStorage(sql, randomUUID);
const legacy = Storage.getItemSync('flights');
storage.migrate(legacy);
if (legacy !== null) Storage.removeItemSync('flights'); // Only retire the old blob after the SQLite transaction commits.
let account = storage.account();
let flights = storage.flights(ownerFor(account));
let accountData = readAccountData();
let settings: Settings = { theme: 'dark', units: 'km', ...JSON.parse(Storage.getItemSync(SETTINGS) ?? '{}') };

const listeners = new Set<() => void>();
const changes = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());
const subscribe = (l: () => void) => { listeners.add(l); return () => { listeners.delete(l); }; };
export const subscribeToFlightChanges = (l: () => void) => { changes.add(l); return () => { changes.delete(l); }; };

function readAccountData() {
  const owner = ownerFor(account);
  return { account, conflicts: storage.conflicts(owner), pending: storage.pendingCount(owner), guestCount: storage.flights('guest').length };
}
function refresh(localChange = false) {
  const next = storage.flights(ownerFor(account));
  // Polling an unchanged account must not rebuild the globe's GPU geometry.
  if (JSON.stringify(next) !== JSON.stringify(flights)) flights = next;
  accountData = readAccountData();
  emit();
  if (localChange) changes.forEach((l) => l());
}

export const useFlights = () => useSyncExternalStore(subscribe, () => flights);
export const useSettings = () => useSyncExternalStore(subscribe, () => settings);
export const useAccountData = () => useSyncExternalStore(subscribe, () => accountData);
export const currentAccount = () => account;

export function addFlights(list: Flight[]) { storage.save(ownerFor(account), list); refresh(true); }
export function removeFlight(id: string) { storage.remove(ownerFor(account), id); refresh(true); }
export function setSettings(patch: Partial<Settings>) {
  const next = { ...settings, ...patch };
  Storage.setItemSync(SETTINGS, JSON.stringify(next));
  settings = next;
  emit();
}
export function activateAccount(value: Account | null) { storage.setAccount(value); account = value; refresh(); }
export function uploadGuestFlights() { storage.uploadGuest(ownerFor(account)); refresh(true); }
export function resolveConflict(id: string, choice: 'local' | 'cloud') { storage.resolve(ownerFor(account), id, choice); refresh(true); }
export function forgetAccount() { storage.forgetAccount(ownerFor(account)); account = null; refresh(); }
export const pendingMutations = (owner: string) => storage.pending(owner);
export function applySync(owner: string, sent: Mutation[], response: SyncResponse) { storage.apply(owner, sent, response); refresh(); }

export const allFlights = () => flights;
export const isDuplicate = (f: Pick<Flight, 'date' | 'from' | 'to' | 'number'>) =>
  flights.some((x) => x.date === f.date && x.from === f.from && x.to === f.to && (x.number ?? '') === (f.number ?? ''));
export const newId = randomUUID;
