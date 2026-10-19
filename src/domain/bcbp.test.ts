import { dateFromDayOfYear, parseBcbp } from './bcbp';

const pass = 'M1' + 'DOE/JOHN'.padEnd(20) + 'E' + 'ABC123 ' + 'SFO' + 'DEN' + 'UA ' + '0123 ' + '287' + 'Y' + '012A' + '0001 ' + '1' + '00';

test('decodes the mandatory items of a boarding pass', () => {
  expect(pass.length).toBe(60);
  expect(parseBcbp(pass)).toEqual({ from: 'SFO', to: 'DEN', airline: 'UA', number: '123', dayOfYear: 287, seat: '12A' });
});

test('rejects strings that are not boarding passes', () => {
  expect(parseBcbp('https://example.com')).toBeNull();
  expect(parseBcbp('')).toBeNull();
});

test('picks the year that puts the day closest to today', () => {
  expect(dateFromDayOfYear(287, new Date('2026-10-13T00:00:00Z'))).toBe('2026-10-14');
  expect(dateFromDayOfYear(360, new Date('2026-01-05T00:00:00Z'))).toBe('2025-12-26');
  expect(dateFromDayOfYear(5, new Date('2026-12-30T00:00:00Z'))).toBe('2027-01-05');
});
