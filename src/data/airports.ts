import type { Airport, AirportIndex } from '../domain/types';
import raw from './airports.json';

export const AIRPORTS = raw as Airport[];
export const AIRPORT_INDEX: AirportIndex = Object.fromEntries(AIRPORTS.map((a) => [a.iata, a]));

export function searchAirports(q: string, limit = 6): Airport[] {
  const s = q.trim().toLowerCase();
  if (!s) return [];
  const code = s.toUpperCase();
  const exact = AIRPORT_INDEX[code];
  const rest: Airport[] = [];
  for (const a of AIRPORTS) {
    if (a === exact) continue;
    if (a.iata.startsWith(code) || a.city.toLowerCase().startsWith(s) || a.name.toLowerCase().includes(s)) {
      rest.push(a);
      if (rest.length >= limit) break;
    }
  }
  return exact ? [exact, ...rest.slice(0, limit - 1)] : rest;
}
