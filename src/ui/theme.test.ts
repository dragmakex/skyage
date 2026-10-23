import { createElement } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import * as SystemUI from 'expo-system-ui';
import { useSettings } from '../store';
import { ThemeProvider, useTheme } from './theme';
import { palette } from './tokens';

jest.mock('../store', () => ({ useSettings: jest.fn() }));
jest.mock('react-native', () => ({ Appearance: { setColorScheme: jest.fn() }, Platform: { OS: 'android' } }));
jest.mock('expo-system-ui', () => ({ setBackgroundColorAsync: jest.fn(async () => {}) }));
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let tree: ReactTestRenderer;
let warning: jest.SpyInstance;
beforeEach(() => {
  jest.mocked(SystemUI.setBackgroundColorAsync).mockResolvedValue(undefined);
  const original = console.error;
  warning = jest.spyOn(console, 'error').mockImplementation((...args) => {
    if (String(args[0]).startsWith('react-test-renderer is deprecated')) return;
    original(...args);
  });
});
afterEach(async () => { if (tree) await act(async () => tree.unmount()); warning.mockRestore(); });

function ReadTheme() {
  const { c, mode } = useTheme();
  expect(c).toBe(palette[mode]);
  return mode;
}

test.each(['dark', 'light', 'system'] as const)('applies %s preference, with legacy system falling back to dark', async (theme) => {
  jest.mocked(useSettings).mockReturnValue({ theme, units: 'km' });
  const mode = theme === 'light' ? 'light' : 'dark';
  await act(async () => { tree = create(createElement(ThemeProvider, null, createElement(ReadTheme))); });
  expect(tree.toJSON()).toBe(mode);
});

test('Android blur stays gated until the matching native window color is applied, ignoring stale theme requests', async () => {
  const pending: (() => void)[] = [];
  jest.mocked(SystemUI.setBackgroundColorAsync).mockImplementation(() => new Promise<void>((resolve) => pending.push(resolve)));
  let latest!: ReturnType<typeof useTheme>;
  function Probe() { latest = useTheme(); return null; }
  jest.mocked(useSettings).mockReturnValue({ theme: 'dark', units: 'km' });
  await act(async () => { tree = create(createElement(ThemeProvider, null, createElement(Probe))); });
  expect(latest.nativeBackgroundReady).toBe(false);
  jest.mocked(useSettings).mockReturnValue({ theme: 'light', units: 'km' });
  await act(async () => tree.update(createElement(ThemeProvider, null, createElement(Probe))));
  await act(async () => pending[0]());
  expect(latest.mode).toBe('light');
  expect(latest.nativeBackgroundReady).toBe(false);
  await act(async () => pending[1]());
  expect(latest.nativeBackgroundReady).toBe(true);
  expect(SystemUI.setBackgroundColorAsync).toHaveBeenLastCalledWith(palette.light.background);
});
