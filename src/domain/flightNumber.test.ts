import { parseFlightNumber } from './flightNumber';

test('splits carrier and number', () => {
  expect(parseFlightNumber('UA 123')).toEqual({ airline: 'UA', number: '123' });
  expect(parseFlightNumber('ua123')).toEqual({ airline: 'UA', number: '123' });
  expect(parseFlightNumber('LH1234')).toEqual({ airline: 'LH', number: '1234' });
  expect(parseFlightNumber('3U 8888')).toEqual({ airline: '3U', number: '8888' });
  expect(parseFlightNumber('UA 0123')).toEqual({ airline: 'UA', number: '123' });
  expect(parseFlightNumber('hello')).toBeNull();
});
