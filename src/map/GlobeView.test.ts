import { createElement } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { AppState } from 'react-native';
import { toGeoJSON } from '../domain/arcs';
import { GlobeView } from './GlobeView';

let mockFocus: () => (() => void) | undefined;
let mockBlur: (() => void) | undefined;
let mockAppState: (state: string) => void;
const mockSessions: { ready: () => void; fail: (error: Error) => void; stop: jest.Mock; connect: jest.Mock; update: jest.Mock; resize: jest.Mock; focus: jest.Mock }[] = [];
jest.mock('expo-router', () => ({ useFocusEffect: (callback: () => (() => void) | undefined) => {
  require('react').useEffect(() => { mockFocus = callback; mockBlur = callback(); return () => mockBlur?.(); }, [callback]);
} }));
jest.mock('expo-gl', () => ({ GLView: 'GLView' }));
jest.mock('react-native', () => ({
  View: 'View', StyleSheet: { absoluteFill: {} },
  PanResponder: { create: () => ({ panHandlers: {} }) },
  AppState: { currentState: 'active', addEventListener: (_: string, callback: (value: string) => void) => { mockAppState = callback; return { remove: jest.fn() }; } },
  AccessibilityInfo: { isReduceMotionEnabled: async () => false, addEventListener: () => ({ remove: jest.fn() }) },
}));
jest.mock('../ui/theme', () => ({ useTheme: () => ({ c: require('../ui/tokens').palette.dark }) }));
jest.mock('../ui/Button', () => ({ Button: 'Button' }));
jest.mock('./GlobePreview', () => ({ GlobePreview: 'GlobePreview' }));
jest.mock('./renderer', () => ({ GlobeRenderer: jest.fn().mockImplementation((ready, fail) => {
  const session = { ready, fail, stop: jest.fn(), connect: jest.fn(), update: jest.fn(), resize: jest.fn(), focus: jest.fn() };
  mockSessions.push(session); return session;
}) }));
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const props = { data: toGeoJSON([], {}), center: [0, 20] as [number, number], onCity: jest.fn(), onFlight: jest.fn(), onClear: jest.fn() };
let tree: ReactTestRenderer;
let errors: jest.SpyInstance;
let warnings: jest.SpyInstance;
const hosts = (type: string) => tree.root.findAll((node) => node.type === type);

beforeEach(() => {
  jest.useFakeTimers({ doNotFake: ['queueMicrotask', 'nextTick', 'setImmediate'] }); mockSessions.length = 0;
  (AppState as { currentState: string }).currentState = 'active';
  const original = console.error;
  errors = jest.spyOn(console, 'error').mockImplementation((...args) => {
    if (String(args[0]).startsWith('react-test-renderer is deprecated')) return;
    original(...args);
  });
  warnings = jest.spyOn(console, 'warn').mockImplementation(() => {});
});
afterEach(async () => {
  try {
    if (tree) await act(async () => tree.unmount());
    expect(jest.getTimerCount()).toBe(0);
  } finally { jest.useRealTimers(); errors.mockRestore(); warnings.mockRestore(); }
});
async function mount() {
  await act(async () => { tree = create(createElement(GlobeView, props)); });
  expect(hosts('GlobePreview')).toHaveLength(1);
  expect(hosts('GLView')).toHaveLength(0); // Wait for real layout instead of rendering a 1x1 globe.
  await act(async () => hosts('View')[0].props.onLayout({ nativeEvent: { layout: { width: 400, height: 600 } } }));
}
function connect() { hosts('GLView')[0].props.onContextCreate({}); }

test('preview remains until the first successful frame, then failures recover on a fresh surface', async () => {
  await mount();
  const staleContext = hosts('GLView')[0].props.onContextCreate;
  await act(async () => connect());
  expect(hosts('GlobePreview')).toHaveLength(1);
  await act(async () => mockSessions[0].ready());
  expect(hosts('GlobePreview')).toHaveLength(0);
  await act(async () => mockSessions[0].fail(new Error('destroyed surface')));
  expect(hosts('GlobePreview')).toHaveLength(1);
  await act(async () => jest.advanceTimersByTime(500));
  await act(async () => staleContext({}));
  expect(mockSessions).toHaveLength(1); // Ignore late native events from the detached surface.
  await act(async () => { connect(); mockSessions[1].ready(); });
  expect(hosts('GlobePreview')).toHaveLength(0);
});

test('navigation blur and backgrounding stop GL work; focus and foreground recreate it', async () => {
  await mount();
  await act(async () => { connect(); mockSessions[0].ready(); });
  await act(async () => mockBlur?.());
  expect(mockSessions[0].stop).toHaveBeenCalled();
  expect(hosts('GLView')).toHaveLength(0);
  expect(hosts('GlobePreview')).toHaveLength(1);
  await act(async () => { mockBlur = mockFocus(); });
  await act(async () => { connect(); mockSessions[1].ready(); });
  await act(async () => mockAppState('background'));
  expect(mockSessions[1].stop).toHaveBeenCalled();
  expect(hosts('GLView')).toHaveLength(0);
  await act(async () => mockAppState('active'));
  expect(hosts('GLView')).toHaveLength(1);
  expect(hosts('GlobePreview')).toHaveLength(1);
  await act(async () => { connect(); mockSessions[2].ready(); });
  expect(hosts('GlobePreview')).toHaveLength(0);
});

test('missing native surface callbacks time out safely, with a working manual retry', async () => {
  await mount();
  await act(async () => jest.advanceTimersByTime(5000));
  expect(hosts('GlobePreview')).toHaveLength(1);
  expect(hosts('Button')[0].props.title).toBe('Retry interactive globe');
  await act(async () => hosts('Button')[0].props.onPress());
  await act(async () => { connect(); mockSessions[0].ready(); });
  expect(hosts('GlobePreview')).toHaveLength(0);
});
