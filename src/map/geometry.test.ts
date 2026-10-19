import { PerspectiveCamera, SphereGeometry } from 'three';
import type { LngLat } from '../domain/geo';
import mask from './earth-mask.json';
import { globePoint, routePoints, routeView } from './geometry';

test('coordinates match the sphere texture orientation', () => {
  expect(globePoint([0, 0]).toArray()).toEqual([0, 0, 1]);
  expect(globePoint([90, 0]).x).toBeCloseTo(1);
  expect(globePoint([0, 90]).y).toBeCloseTo(1);
  const sphere = new SphereGeometry(1, 8, 4).rotateY(-Math.PI / 2);
  const positions = sphere.attributes.position;
  const uv = sphere.attributes.uv;
  for (let i = 0; i < positions.count; i++) {
    const expected = globePoint([uv.getX(i) * 360 - 180, uv.getY(i) * 180 - 90]);
    expect(positions.getX(i)).toBeCloseTo(expected.x, 5);
    expect(positions.getY(i)).toBeCloseTo(expected.y, 5);
    expect(positions.getZ(i)).toBeCloseTo(expected.z, 5);
  }
  sphere.dispose();
});

test.each<[LngLat, LngLat]>([
  [[139.78, 35.55], [-122.38, 37.62]],
  [[0, 0], [180, 0]],
  [[0, 90], [0, -90]],
  [[10, 20], [10, 20]],
])('routes have finite points on the sphere, including antipodes and identical airports: %j to %j', (a, b) => {
  const points = routePoints(a, b);
  expect(points).toHaveLength(97);
  expect(points[0].distanceTo(globePoint(a, 1.006))).toBeLessThan(1e-6);
  expect(points[96].distanceTo(globePoint(b, 1.006))).toBeLessThan(1e-6);
  for (const p of points) expect(p.length()).toBeCloseTo(1.006);
  for (let i = 1; i < points.length; i++) expect(points[i].distanceTo(points[i - 1])).toBeLessThan(0.04);
});

test.each([0.5, 1, 2])('route focus fits all arc points at aspect %s', (aspect) => {
  const points = routePoints([139.78, 35.55], [-122.38, 37.62]);
  const view = routeView(points, aspect);
  const camera = new PerspectiveCamera(40, aspect, 0.05, 100);
  camera.position.copy(globePoint([view.lon, view.lat], view.distance));
  camera.lookAt(0, 0, 0);
  camera.updateMatrixWorld();
  for (const point of points) {
    const projected = point.clone().project(camera);
    expect(Math.abs(projected.x)).toBeLessThan(1);
    expect(Math.abs(projected.y)).toBeLessThan(1);
  }
});

test('bundled earth mask fills the texture and contains only sea, land and borders', () => {
  let pixels = 0;
  expect(mask.runs.length % 2).toBe(0);
  for (let i = 0; i < mask.runs.length; i += 2) {
    expect([0, 1, 2]).toContain(mask.runs[i]);
    expect(mask.runs[i + 1]).toBeGreaterThan(0);
    pixels += mask.runs[i + 1];
  }
  expect(pixels).toBe(mask.width * mask.height);
});
