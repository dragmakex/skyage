import { parseCsv, parseImport } from './csv';

test('header plus two rows yields two flights, a bad row is skipped', () => {
  const text = 'date,from,to,flight\n2025-03-01,sfo,den,UA 123\nnot-a-date,SFO,DEN,\n2025-06-01,DEN,JFK,\n';
  expect(parseCsv(text)).toEqual([
    { date: '2025-03-01', from: 'SFO', to: 'DEN', airline: 'UA', number: '123' },
    { date: '2025-06-01', from: 'DEN', to: 'JFK' },
  ]);
});

test('import accepts the JSON export and drops ids', () => {
  const json = JSON.stringify([{ id: 'x', date: '2025-03-01', from: 'SFO', to: 'DEN', source: 'scan', seat: '12A' }, { date: 'bad' }]);
  expect(parseImport(json)).toEqual([{ date: '2025-03-01', from: 'SFO', to: 'DEN', seat: '12A' }]);
});
