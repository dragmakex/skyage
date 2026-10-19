export type Flight = {
  id: string;
  date: string; // YYYY-MM-DD, local departure date
  from: string; // IATA
  to: string; // IATA
  airline?: string; // IATA carrier, "UA"
  number?: string; // "123"
  seat?: string;
  depTime?: string; // ISO, when the lookup gave it
  arrTime?: string;
  source: 'scan' | 'lookup' | 'manual' | 'import';
};

export type Airport = { iata: string; name: string; city: string; country: string; lat: number; lon: number };
export type AirportIndex = Record<string, Airport>;
