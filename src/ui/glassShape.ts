// The highlight follows the actual curved perimeter, never a straight border across a circle's top.
export function glassOutline(width: number, height: number, corners: readonly [number, number, number, number]) {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 1 || height <= 1) return '';
  const right = width - 0.5, bottom = height - 0.5;
  const limit = Math.min(width - 1, height - 1) / 2;
  const [tl, tr, br, bl] = corners.map((v) => Math.max(0, Math.min(Number.isFinite(v) ? v : 0, limit)));
  return `M ${0.5 + tl} 0.5 H ${right - tr} A ${tr} ${tr} 0 0 1 ${right} ${0.5 + tr} V ${bottom - br} A ${br} ${br} 0 0 1 ${right - br} ${bottom} H ${0.5 + bl} A ${bl} ${bl} 0 0 1 0.5 ${bottom - bl} V ${0.5 + tl} A ${tl} ${tl} 0 0 1 ${0.5 + tl} 0.5 Z`;
}
