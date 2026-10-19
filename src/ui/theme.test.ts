import { createElement, type ReactNode } from 'react';
import { useSettings } from '../store';
import { ThemeProvider, useTheme } from './theme';
import { palette } from './tokens';

jest.mock('../store', () => ({ useSettings: jest.fn() }));
jest.mock('react-native', () => ({ Appearance: { setColorScheme: jest.fn() } }));

const { renderToStaticMarkup } = require('react-dom/server') as { renderToStaticMarkup: (node: ReactNode) => string };

function ReadTheme() {
  const { c, mode } = useTheme();
  expect(c).toBe(palette[mode]);
  return mode;
}

test.each(['dark', 'light', 'system'] as const)('applies %s preference, with legacy system falling back to dark', (theme) => {
  jest.mocked(useSettings).mockReturnValue({ theme, units: 'km' });
  const mode = theme === 'light' ? 'light' : 'dark';
  expect(renderToStaticMarkup(createElement(ThemeProvider, null, createElement(ReadTheme))))
    .toBe(mode);
});
