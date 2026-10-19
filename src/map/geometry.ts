import { Vector3 } from 'three';
import type { LngLat } from '../domain/geo';

export function globePoint([lon, lat]: LngLat, radius = 1) {
  const phi = lat * Math.PI / 180;
  const theta = lon * Math.PI / 180;
  return new Vector3(Math.cos(phi) * Math.sin(theta), Math.sin(phi), Math.cos(phi) * Math.cos(theta)).multiplyScalar(radius);
}

export function routePoints(a: LngLat, b: LngLat, steps = 96) {
  const start = globePoint(a);
  const end = globePoint(b);
  const angle = Math.acos(Math.max(-1, Math.min(1, start.dot(end))));
  let tangent = end.clone().addScaledVector(start, -start.dot(end));
  // Antipodal airports have no unique great circle; choose a stable perpendicular.
  if (tangent.lengthSq() < 1e-12) tangent = new Vector3(0, 1, 0).cross(start);
  if (tangent.lengthSq() < 1e-12) tangent = new Vector3(1, 0, 0).cross(start);
  tangent.normalize();
  return Array.from({ length: steps + 1 }, (_, i) => {
    if (i === steps) return end.clone().multiplyScalar(1.006);
    const t = angle * i / steps;
    return start.clone().multiplyScalar(Math.cos(t)).addScaledVector(tangent, Math.sin(t)).multiplyScalar(1.006);
  });
}

export function routeView(points: Vector3[], aspect: number) {
  const direction = points[Math.floor(points.length / 2)].clone().normalize();
  const lat = Math.asin(direction.y) * 180 / Math.PI;
  const lon = Math.atan2(direction.x, direction.z) * 180 / Math.PI;
  const tan = Math.tan(20 * Math.PI / 180) * Math.min(1, aspect) / 1.15;
  const distance = Math.max(2, ...points.map((p) => {
    const depth = p.dot(direction);
    return depth + Math.sqrt(Math.max(0, p.lengthSq() - depth * depth)) / tan;
  }));
  return { lon, lat, distance };
}
