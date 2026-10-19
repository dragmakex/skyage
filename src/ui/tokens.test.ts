import { palette, PEEK_FRACTION, SHEET_SNAP_POINTS } from './tokens';

function luminance(hex: string) {
  const channels = [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16) / 255);
  const [r, g, b] = channels.map((v) => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string) {
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (values[0] + 0.05) / (values[1] + 0.05);
}

test('dark text, controls and light-blue paths have readable contrast', () => {
  const c = palette.dark;
  expect(contrast(c.foreground, c.background)).toBeGreaterThan(7);
  expect(contrast(c.mutedForeground, c.surface)).toBeGreaterThan(4.5);
  expect(contrast(c.flight, c.land)).toBeGreaterThan(7);
  expect(contrast(c.background, c.foreground)).toBeGreaterThan(4.5);
});

test('light mode text and flight paths remain readable on pale surfaces', () => {
  const c = palette.light;
  expect(contrast(c.foreground, c.surface)).toBeGreaterThan(7);
  expect(contrast(c.mutedForeground, c.surface)).toBeGreaterThan(4.5);
  expect(contrast(c.flight, c.land)).toBeGreaterThan(4.5);
});

test('flight menu starts at 20% and can expand to half and nearly full screen', () => {
  expect(SHEET_SNAP_POINTS).toEqual(['20%', '50%', '92%']);
  expect(PEEK_FRACTION).toBe(parseFloat(SHEET_SNAP_POINTS[0]) / 100);
});
