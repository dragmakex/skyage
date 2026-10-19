const dateFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const numFmt = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

export const fmtDate = (iso: string) => dateFmt.format(new Date(`${iso}T00:00:00Z`));

export function fmtDistance(km: number, units: 'km' | 'mi') {
  return `${numFmt.format(units === 'mi' ? km * 0.621371 : km)} ${units}`;
}

export function fmtDuration(dep?: string, arr?: string) {
  if (!dep || !arr) return undefined;
  const m = Math.round((Date.parse(arr) - Date.parse(dep)) / 60000);
  if (!(m > 0)) return undefined;
  return `${Math.floor(m / 60)} h ${m % 60} min`;
}

export const isoToday = () => new Date().toISOString().slice(0, 10);

export const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
