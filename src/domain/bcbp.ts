// IATA Bar Coded Boarding Pass (BCBP), mandatory items of the first leg only.
export type Bcbp = { from: string; to: string; airline: string; number: string; dayOfYear: number; seat?: string };

export function parseBcbp(raw: string): Bcbp | null {
  if (!raw || raw[0] !== 'M' || raw.length < 58) return null;
  const f = (s: number, e: number) => raw.slice(s, e).trim();
  const from = f(30, 33);
  const to = f(33, 36);
  const airline = f(36, 39);
  const number = f(39, 44).replace(/[A-Z]$/, '').replace(/^0+/, '');
  const dayOfYear = parseInt(f(44, 47), 10);
  if (!/^[A-Z]{3}$/.test(from) || !/^[A-Z]{3}$/.test(to)) return null;
  if (!/^[A-Z0-9]{2,3}$/.test(airline) || !/^\d{1,4}$/.test(number)) return null;
  if (!(dayOfYear >= 1 && dayOfYear <= 366)) return null;
  const seatRaw = f(48, 52).replace(/^0+/, '');
  return { from, to, airline, number, dayOfYear, seat: seatRaw || undefined };
}

// BCBP carries no year. Pick the year that puts the date closest to today.
export function dateFromDayOfYear(dayOfYear: number, today = new Date()): string {
  const y = today.getUTCFullYear();
  let best = new Date(Date.UTC(y, 0, dayOfYear));
  for (const yy of [y - 1, y + 1]) {
    const d = new Date(Date.UTC(yy, 0, dayOfYear));
    if (Math.abs(d.getTime() - today.getTime()) < Math.abs(best.getTime() - today.getTime())) best = d;
  }
  return best.toISOString().slice(0, 10);
}
