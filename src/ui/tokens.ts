export type Mode = 'light' | 'dark';

// shadcn zinc values, shadcn names. Nothing outside this file hard-codes a color, radius or size.
export const palette = {
  light: {
    background: '#FAFAFA',
    foreground: '#09090B',
    muted: '#F4F4F5',
    mutedForeground: '#71717A',
    border: '#E4E4E7',
    land: '#E4E4E7',
    ring: '#71717A',
    flight: '#0369A1',
    surface: '#FAFAFA',
  },
  dark: {
    background: '#09090B',
    foreground: '#FAFAFA',
    muted: '#1C1C20',
    mutedForeground: '#A1A1AA',
    border: '#303742',
    land: '#161C24',
    ring: '#71717A',
    flight: '#9BD8FF',
    surface: '#111113',
  },
} as const;
export type Colors = { [K in keyof typeof palette.light]: string };

// Glass surfaces are capsules or 28 at the sheet top. Everything inside the sheet is 12.
export const radius = { sheet: 28, control: 12, pill: 999 } as const;

export const font = {
  sans: 'Geist_400Regular',
  sansMedium: 'Geist_500Medium',
  sansSemi: 'Geist_600SemiBold',
  mono: 'GeistMono_400Regular',
  monoSemi: 'GeistMono_600SemiBold',
} as const;

export const typeScale = {
  display: { fontSize: 32, lineHeight: 36 },
  stat: { fontSize: 22, lineHeight: 28 },
  title: { fontSize: 18, lineHeight: 24 },
  body: { fontSize: 14, lineHeight: 20 },
  caption: { fontSize: 12, lineHeight: 16 },
} as const;
export type Role = keyof typeof typeScale;

export const SHEET_SNAP_POINTS = ['20%', '50%', '92%'];
export const PEEK_FRACTION = 0.2;

// ponytail: flip to false if the map under the glass drops frames on a mid-range device
export const GLASS_BLUR = true;

export function alpha(hex: string, a: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}
