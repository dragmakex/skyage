import Storage from 'expo-sqlite/kv-store';
import { useSyncExternalStore } from 'react';
import type { Flight } from './domain/types';

export type Settings = { theme: 'system' | 'light' | 'dark'; units: 'km' | 'mi' };

const FLIGHTS = 'flights';
const SETTINGS = 'settings';

// ponytail: one JSON blob; move to SQLite tables past a few thousand flights.
let flights: Flight[] = sortDesc(JSON.parse(Storage.getItemSync(FLIGHTS) ?? '[]'));
let settings: Settings = { theme: 'dark', units: 'km', ...JSON.parse(Storage.getItemSync(SETTINGS) ?? '{}') };

const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());
const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => void listeners.delete(l);
};

function sortDesc(list: Flight[]) {
  return [...list].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
}

export const useFlights = () => useSyncExternalStore(subscribe, () => flights);
export const useSettings = () => useSyncExternalStore(subscribe, () => settings);

export function addFlights(list: Flight[]) {
  flights = sortDesc([...flights, ...list]);
  Storage.setItemSync(FLIGHTS, JSON.stringify(flights));
  emit();
}

export function removeFlight(id: string) {
  flights = flights.filter((f) => f.id !== id);
  Storage.setItemSync(FLIGHTS, JSON.stringify(flights));
  emit();
}

export function setSettings(patch: Partial<Settings>) {
  settings = { ...settings, ...patch };
  Storage.setItemSync(SETTINGS, JSON.stringify(settings));
  emit();
}

export const allFlights = () => flights;

export const isDuplicate = (f: Pick<Flight, 'date' | 'from' | 'to' | 'number'>) =>
  flights.some((x) => x.date === f.date && x.from === f.from && x.to === f.to && (x.number ?? '') === (f.number ?? ''));

export const newId = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
