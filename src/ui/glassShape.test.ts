import { glassOutline } from './glassShape';

test('a circle has a curved perimeter rather than a straight top highlight', () => {
  const path = glassOutline(44, 44, [999, 999, 999, 999]);
  expect(path).toMatch(/^M 22 0\.5 H 22 A 21\.5 21\.5/);
  expect(path.match(/ A /g)).toHaveLength(4);
});

test('pills and sheets clamp radii to their own bounds and retain square bottom corners', () => {
  expect(glassOutline(120, 44, [999, 999, 999, 999])).toContain('H 98 A 21.5 21.5');
  expect(glassOutline(400, 600, [28, 28, 0, 0])).toContain('A 0 0 0 0 1 399.5 599.5');
  expect(glassOutline(44, 44, [-1, NaN, 0, 0])).not.toMatch(/NaN|-1/);
});

test('unmeasured or invalid surfaces do not produce invalid SVG geometry', () => {
  expect(glassOutline(0, 0, [0, 0, 0, 0])).toBe('');
  expect(glassOutline(NaN, 44, [0, 0, 0, 0])).toBe('');
});
