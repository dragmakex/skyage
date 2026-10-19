import { bbox, greatCircle, haversineKm } from './geo';

const SFO: [number, number] = [-122.375, 37.619];
const DEN: [number, number] = [-104.673, 39.8617];
const NRT: [number, number] = [140.386, 35.7647];

test('SFO to DEN is about 1,550 km', () => {
  expect(Math.round(haversineKm(SFO, DEN))).toBeGreaterThan(1500);
  expect(Math.round(haversineKm(SFO, DEN))).toBeLessThan(1600);
});

test('arc starts and ends at the airports', () => {
  const arc = greatCircle(SFO, DEN);
  expect(arc).toHaveLength(65);
  expect(arc[0]).toEqual(SFO);
  expect(arc[64][0]).toBeCloseTo(DEN[0], 3);
  expect(arc[64][1]).toBeCloseTo(DEN[1], 3);
});

test('a Pacific crossing never jumps across the antimeridian', () => {
  const arc = greatCircle(NRT, SFO);
  for (let i = 1; i < arc.length; i++) expect(Math.abs(arc[i][0] - arc[i - 1][0])).toBeLessThan(90);
  const [[w], [e]] = bbox(arc);
  expect(e - w).toBeLessThan(180);
});
