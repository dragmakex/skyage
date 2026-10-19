export type Leg = { from: string; to: string; depTime?: string; arrTime?: string };

const KEY = process.env.EXPO_PUBLIC_AERODATABOX_KEY;
const HOST = 'aerodatabox.p.rapidapi.com';

// ponytail: the key ships in the APK, fine for a personal build; put a 30-line proxy in front before a store release.
export async function lookupFlight(airline: string, number: string, date: string): Promise<Leg[]> {
  if (!KEY) throw new Error('No lookup key configured. Set EXPO_PUBLIC_AERODATABOX_KEY.');
  const res = await fetch(`https://${HOST}/flights/number/${airline}${number}/${date}?withAircraftImage=false&withLocation=false`, {
    headers: { 'x-rapidapi-key': KEY, 'x-rapidapi-host': HOST },
  });
  if (res.status === 204 || res.status === 404) return [];
  if (!res.ok) throw new Error(`Lookup failed (${res.status}).`);
  const data: unknown = await res.json();
  if (!Array.isArray(data)) return [];
  const iso = (s: unknown) => (typeof s === 'string' ? s.replace(' ', 'T') : undefined);
  return data.flatMap((l): Leg[] => {
    const from: unknown = l?.departure?.airport?.iata;
    const to: unknown = l?.arrival?.airport?.iata;
    if (typeof from !== 'string' || typeof to !== 'string') return [];
    return [{ from, to, depTime: iso(l?.departure?.scheduledTime?.utc), arrTime: iso(l?.arrival?.scheduledTime?.utc) }];
  });
}
