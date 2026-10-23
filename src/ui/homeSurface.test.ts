import { createElement } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import Home from '../../app/index';

let mockMounts = 0;
let mockForeground: (state: string) => void;
let mockFocus: () => (() => void) | undefined;
let mockBlur: (() => void) | undefined;
const mockFlights: unknown[] = [];
jest.mock('expo-router', () => ({ router: { push: jest.fn() }, useFocusEffect: (callback: () => (() => void) | undefined) => {
  require('react').useEffect(() => { mockFocus = callback; mockBlur = callback(); return () => mockBlur?.(); }, [callback]);
} }));
jest.mock('@gorhom/bottom-sheet', () => {
  const React = require('react');
  return { __esModule: true, default: React.forwardRef((props: object, ref: unknown) => {
    const [surfaceId] = React.useState(() => ++mockMounts);
    React.useImperativeHandle(ref, () => ({ snapToIndex: jest.fn() }));
    return React.createElement('BottomSheet', { ...props, surfaceId });
  }), BottomSheetScrollView: 'BottomSheetScrollView', BottomSheetSectionList: 'BottomSheetSectionList' };
});
jest.mock('react-native', () => ({
  View: 'View', Pressable: 'Pressable', ScrollView: 'ScrollView', Alert: { alert: jest.fn() },
  StyleSheet: { absoluteFill: {}, hairlineWidth: 1 }, useWindowDimensions: () => ({ height: 900, width: 400 }),
  AppState: { addEventListener: (_: string, callback: (state: string) => void) => { mockForeground = callback; return { remove: jest.fn() }; } },
}));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 20, bottom: 20 }) }));
jest.mock('expo-blur', () => ({ BlurTargetView: 'BlurTargetView' }));
jest.mock('phosphor-react-native', () => ({ Gear: 'Gear', Plus: 'Plus', Trash: 'Trash', X: 'X' }));
jest.mock('../data/airports', () => ({ AIRPORT_INDEX: {} }));
jest.mock('../map/GlobeView', () => ({ GlobeView: 'GlobeView' }));
jest.mock('../store', () => ({ useFlights: () => mockFlights, useSettings: () => ({ units: 'km' }), removeFlight: jest.fn() }));
jest.mock('./theme', () => ({ useTheme: () => ({ c: require('./tokens').palette.dark }), Txt: 'Text' }));
jest.mock('./Button', () => ({ Button: 'Button' }));
jest.mock('./Chip', () => ({ Chip: 'Chip' }));
jest.mock('./FlightRow', () => ({ FlightRow: 'FlightRow' }));
jest.mock('./EmptyState', () => ({ EmptyState: 'EmptyState' }));
jest.mock('./Glass', () => ({ Glass: 'Glass', BlurTargetContext: require('react').createContext({ current: null }) }));

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
test('the flight menu restores its native surface and snap index after resume and navigation, never accepting a hidden index', async () => {
  const original = console.error;
  const warning = jest.spyOn(console, 'error').mockImplementation((...args) => {
    if (String(args[0]).startsWith('react-test-renderer is deprecated')) return;
    original(...args);
  });
  let tree!: ReactTestRenderer;
  const sheet = () => tree.root.find((node) => String(node.type) === 'BottomSheet');
  try {
    await act(async () => { tree = create(createElement(Home)); });
    const initial = sheet().props.surfaceId;
    await act(async () => sheet().props.onChange(2));
    expect(sheet().props.index).toBe(2);
    await act(async () => sheet().props.onChange(-1));
    expect(sheet().props.index).toBe(2);
    await act(async () => { mockForeground('background'); mockForeground('active'); });
    expect(sheet().props.surfaceId).toBeGreaterThan(initial);
    expect(sheet().props.index).toBe(2);
    const resumed = sheet().props.surfaceId;
    await act(async () => { mockBlur?.(); mockBlur = mockFocus(); });
    expect(sheet().props.surfaceId).toBeGreaterThan(resumed);
    expect(sheet().props.enablePanDownToClose).toBe(false);
  } finally { if (tree) await act(async () => tree.unmount()); warning.mockRestore(); }
});
