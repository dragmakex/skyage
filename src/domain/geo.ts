export type LngLat = [number, number];

const R = 6371;
const rad = (d: number) => (d * Math.PI) / 180;
const deg = (r: number) => (r * 180) / Math.PI;

export function haversineKm(a: LngLat, b: LngLat): number {
  const dLat = rad(b[1] - a[1]);
  const dLng = rad(b[0] - a[0]);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[1])) * Math.cos(rad(b[1])) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// Points along the great circle from a to b, with longitudes unwrapped across the antimeridian.
export function greatCircle(a: LngLat, b: LngLat, n = 64): LngLat[] {
  const l1 = rad(a[0]), p1 = rad(a[1]), l2 = rad(b[0]), p2 = rad(b[1]);
  const d = 2 * Math.asin(Math.sqrt(Math.sin((p2 - p1) / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin((l2 - l1) / 2) ** 2));
  if (d === 0) return [a, b];
  const out: LngLat[] = [];
  for (let i = 0; i <= n; i++) {
    const f = i / n;
    const A = Math.sin((1 - f) * d) / Math.sin(d);
    const B = Math.sin(f * d) / Math.sin(d);
    const x = A * Math.cos(p1) * Math.cos(l1) + B * Math.cos(p2) * Math.cos(l2);
    const y = A * Math.cos(p1) * Math.sin(l1) + B * Math.cos(p2) * Math.sin(l2);
    const z = A * Math.sin(p1) + B * Math.sin(p2);
    let lng = deg(Math.atan2(y, x));
    const prev = out[out.length - 1];
    if (prev) {
      if (lng - prev[0] > 180) lng -= 360;
      if (lng - prev[0] < -180) lng += 360;
    }
    out.push([lng, deg(Math.atan2(z, Math.sqrt(x * x + y * y)))]);
  }
  out[0] = [a[0], a[1]];
  return out;
}

export function bbox(points: LngLat[]): [LngLat, LngLat] {
  let w = Infinity, s = Infinity, e = -Infinity, n = -Infinity;
  for (const [x, y] of points) {
    if (x < w) w = x;
    if (x > e) e = x;
    if (y < s) s = y;
    if (y > n) n = y;
  }
  return [[w, s], [e, n]];
}
