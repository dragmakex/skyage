import { parseFlightNumber } from './flightNumber';
import type { Flight } from './types';

export type ImportedFlight = Omit<Flight, 'id' | 'source'>;

const ISO = /^\d{4}-\d{2}-\d{2}$/;
const IATA = /^[A-Z]{3}$/;

// Columns: date,from,to,flight with a header row. flight is optional.
// ponytail: naive comma split, no quoted fields; enough for a spreadsheet export of four columns.
export function parseCsv(text: string): ImportedFlight[] {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (lines.length < 2) return [];
  const header = lines[0].split(',').map((s) => s.trim().toLowerCase());
  const col = (name: string) => header.indexOf(name);
  const [iDate, iFrom, iTo, iFlight] = [col('date'), col('from'), col('to'), col('flight')];
  if (iDate < 0 || iFrom < 0 || iTo < 0) return [];
  const out: ImportedFlight[] = [];
  for (const line of lines.slice(1)) {
    const cells = line.split(',').map((s) => s.trim());
    const date = cells[iDate] ?? '';
    const from = (cells[iFrom] ?? '').toUpperCase();
    const to = (cells[iTo] ?? '').toUpperCase();
    if (!ISO.test(date) || !IATA.test(from) || !IATA.test(to)) continue;
    const ident = iFlight >= 0 && cells[iFlight] ? parseFlightNumber(cells[iFlight]) : null;
    out.push({ date, from, to, ...(ident ?? {}) });
  }
  return out;
}

// A JSON export of this app, or a CSV.
export function parseImport(text: string): ImportedFlight[] {
  const t = text.trim();
  if (!t.startsWith('[')) return parseCsv(t);
  try {
    const arr: unknown = JSON.parse(t);
    if (!Array.isArray(arr)) return [];
    return arr.flatMap((x) => {
      const f = x as Partial<Flight>;
      if (typeof f.date !== 'string' || !ISO.test(f.date)) return [];
      if (typeof f.from !== 'string' || typeof f.to !== 'string') return [];
      const from = f.from.toUpperCase(), to = f.to.toUpperCase();
      if (!IATA.test(from) || !IATA.test(to)) return [];
      const { id: _id, source: _source, ...rest } = f;
      return [{ ...rest, date: f.date, from, to }];
    });
  } catch {
    return [];
  }
}
