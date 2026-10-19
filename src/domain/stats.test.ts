import { stats } from './stats';
import type { AirportIndex, Flight } from './types';

const index: AirportIndex = {
  SFO: { iata: 'SFO', name: 'SFO', city: 'San Francisco', country: 'US', lat: 37.619, lon: -122.375 },
  DEN: { iata: 'DEN', name: 'DEN', city: 'Denver', country: 'US', lat: 39.8617, lon: -104.673 },
};
const f = (id: string, from: string, to: string): Flight => ({ id, date: '2025-01-01', from, to, source: 'manual' });

test('three flights through two airports', () => {
  const s = stats([f('1', 'SFO', 'DEN'), f('2', 'DEN', 'SFO'), f('3', 'SFO', 'DEN')], index);
  expect(s.flights).toBe(3);
  expect(s.airports).toBe(2);
  expect(s.km).toBeGreaterThan(4500);
  expect(s.km).toBeLessThan(4800);
});
