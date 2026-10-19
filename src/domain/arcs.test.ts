import { toGeoJSON } from './arcs';
import type { AirportIndex, Flight } from './types';

const index: AirportIndex = {
  SFO: { iata: 'SFO', name: 'San Francisco International', city: 'San Francisco', country: 'US', lat: 37.619, lon: -122.375 },
  DEN: { iata: 'DEN', name: 'Denver International', city: 'Denver', country: 'US', lat: 39.8617, lon: -104.673 },
  JFK: { iata: 'JFK', name: 'John F Kennedy', city: 'New York', country: 'US', lat: 40.6398, lon: -73.7789 },
};
const flights: Flight[] = [
  { id: 'a', date: '2025-03-01', from: 'SFO', to: 'DEN', source: 'manual' },
  { id: 'b', date: '2025-06-01', from: 'DEN', to: 'JFK', source: 'manual' },
  { id: 'c', date: '2027-01-01', from: 'JFK', to: 'SFO', source: 'manual' },
  { id: 'd', date: '2025-01-01', from: 'XXX', to: 'SFO', source: 'manual' },
];

test('one line per resolvable flight, city visits counted, one selected', () => {
  const { lines, cities } = toGeoJSON(flights, index, 'b', '2026-10-13');
  expect(lines.features).toHaveLength(3);
  expect(lines.features.filter((f) => f.properties?.selected)).toHaveLength(1);
  expect(lines.features.find((f) => f.properties?.id === 'c')?.properties?.upcoming).toBe(true);
  const visits = Object.fromEntries(cities.features.map((f) => [f.properties?.iata, f.properties?.visits]));
  expect(visits).toEqual({ SFO: 2, DEN: 2, JFK: 2 });
});
