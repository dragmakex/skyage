export function parseFlightNumber(input: string): { airline: string; number: string } | null {
  const m = input.trim().toUpperCase().replace(/\s+/g, '').match(/^([A-Z0-9]{2}[A-Z]?)(\d{1,4})[A-Z]?$/);
  if (!m) return null;
  return { airline: m[1], number: String(parseInt(m[2], 10)) };
}
