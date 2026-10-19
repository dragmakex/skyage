import { haversineKm } from './geo';
import type { AirportIndex, Flight } from './types';

export function stats(flights: Flight[], index: AirportIndex) {
  let km = 0;
  const airports = new Set<string>();
  for (const f of flights) {
    const a = index[f.from];
    const b = index[f.to];
    if (a) airports.add(a.iata);
    if (b) airports.add(b.iata);
    if (a && b) km += haversineKm([a.lon, a.lat], [b.lon, b.lat]);
  }
  return { flights: flights.length, km: Math.round(km), airports: airports.size };
}
