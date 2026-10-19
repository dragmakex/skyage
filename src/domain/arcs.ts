import { isoToday } from './format';
import { greatCircle } from './geo';
import type { AirportIndex, Flight } from './types';

type Feature = GeoJSON.Feature;
const fc = (features: Feature[]): GeoJSON.FeatureCollection => ({ type: 'FeatureCollection', features });

// The map renders from these two collections and nothing else.
export function toGeoJSON(flights: Flight[], index: AirportIndex, selectedId?: string, today = isoToday()) {
  const lines: Feature[] = [];
  const visits = new Map<string, number>();
  for (const f of flights) {
    const a = index[f.from];
    const b = index[f.to];
    if (!a || !b) continue;
    visits.set(a.iata, (visits.get(a.iata) ?? 0) + 1);
    visits.set(b.iata, (visits.get(b.iata) ?? 0) + 1);
    lines.push({
      type: 'Feature',
      id: f.id,
      properties: { id: f.id, year: f.date.slice(0, 4), upcoming: f.date > today, selected: f.id === selectedId },
      geometry: { type: 'LineString', coordinates: greatCircle([a.lon, a.lat], [b.lon, b.lat]) },
    });
  }
  const cities: Feature[] = [...visits].map(([iata, n]) => {
    const a = index[iata];
    return {
      type: 'Feature',
      id: iata,
      properties: { iata, city: a.city, visits: n },
      geometry: { type: 'Point', coordinates: [a.lon, a.lat] },
    };
  });
  return { lines: fc(lines), cities: fc(cities) };
}
