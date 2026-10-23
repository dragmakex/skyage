import { createElement } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { AccessibilityInfo, Platform } from 'react-native';
import { isGlassEffectAPIAvailable } from 'expo-glass-effect';
import { Button } from './Button';
import { Chip } from './Chip';
import { Glass, BlurTargetContext } from './Glass';
import { Field } from './Field';
import { OptionRow } from './OptionRow';
import { palette } from './tokens';

let mockNativeReady = true;
jest.mock('react-native', () => ({
  View: 'View', Pressable: 'Pressable', TextInput: 'TextInput', Text: 'Text',
  Platform: { OS: 'android', Version: 35 },
  StyleSheet: { absoluteFill: { position: 'absolute' }, flatten: (value: unknown) => Object.assign({}, ...[value].flat(Infinity).filter(Boolean)) },
  AccessibilityInfo: { isReduceTransparencyEnabled: jest.fn(async () => false), addEventListener: jest.fn(() => ({ remove: jest.fn() })) },
}));
jest.mock('expo-blur', () => ({ BlurView: 'BlurView' }));
jest.mock('expo-glass-effect', () => ({ GlassView: 'GlassView', isLiquidGlassAvailable: jest.fn(() => true), isGlassEffectAPIAvailable: jest.fn(() => true) }));
jest.mock('react-native-svg', () => ({ __esModule: true, default: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient', Path: 'Path', Stop: 'Stop' }));
jest.mock('phosphor-react-native', () => ({ CaretRight: 'CaretRight' }));
jest.mock('./theme', () => ({
  useTheme: () => ({ c: require('./tokens').palette.dark, mode: 'dark', nativeBackgroundReady: mockNativeReady }),
  Txt: (props: object) => require('react').createElement('Text', props),
}));

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let tree: ReactTestRenderer;
let errors: jest.SpyInstance;
beforeEach(() => {
  mockNativeReady = true;
  (Platform as { OS: string }).OS = 'android';
  jest.mocked(AccessibilityInfo.isReduceTransparencyEnabled).mockResolvedValue(false);
  jest.mocked(isGlassEffectAPIAvailable).mockReturnValue(true);
  const original = console.error;
  errors = jest.spyOn(console, 'error').mockImplementation((...args) => {
    if (String(args[0]).startsWith('react-test-renderer is deprecated')) return;
    original(...args);
  });
});
afterEach(async () => { if (tree) await act(async () => tree.unmount()); errors.mockRestore(); });
async function render(element: ReturnType<typeof createElement>) { await act(async () => { tree = create(element); }); }
const hosts = (type: string) => tree.root.findAll((n) => n.type === type);

// Inspect real shared components, not snapshots of unrelated markup.
test('text and icon buttons keep press handlers, readable labels, circular/pill geometry and disabled semantics', async () => {
  const press = jest.fn();
  await render(createElement(Button, { title: 'Add flight', onPress: press }));
  const button = hosts('Pressable')[0];
  expect(button.props.accessibilityLabel).toBe('Add flight');
  expect(button.props.style({ pressed: true })[0]).toMatchObject({ minHeight: 44, transform: [{ scale: 0.97 }] });
  await act(async () => button.props.onPress());
  expect(press).toHaveBeenCalledTimes(1);
  expect(tree.root.findByType(Glass).props.style.borderRadius).toBe(999);
  await act(async () => tree.update(createElement(Button, { accessibilityLabel: 'Settings', icon: createElement('Icon'), variant: 'secondary', disabled: true })));
  expect(hosts('Pressable')[0].props).toMatchObject({ disabled: true, accessibilityLabel: 'Settings', accessibilityState: { disabled: true } });
  expect(hosts('Pressable')[0].props.style({ pressed: false })[0]).toMatchObject({ width: 44, height: 44 });
});

test('chips and menu rows use the same glass component without a second enclosing pill', async () => {
  await render(createElement(Chip, { label: 'All flights', selected: true, onPress: jest.fn() }));
  expect(tree.root.findAllByType(Glass)).toHaveLength(1);
  expect(tree.root.findByType(Glass).props.tone).toBe('selected');
  expect(hosts('Pressable')[0].props.accessibilityState.selected).toBe(true);
  await act(async () => tree.update(createElement(OptionRow, { icon: createElement('Icon'), title: 'Route', description: 'Works offline', onPress: jest.fn() })));
  expect(tree.root.findAllByType(Glass)).toHaveLength(1);
  expect(hosts('Pressable')[0].props.accessibilityLabel).toBe('Route');
});

test('glass fields retain text input events and have no opaque input background covering the material', async () => {
  const change = jest.fn();
  await render(createElement(Field, { label: 'From', value: 'LHR', onChangeText: change }));
  expect(tree.root.findAllByType(Glass)).toHaveLength(1);
  const input = hosts('TextInput')[0];
  await act(async () => input.props.onChangeText('JFK'));
  expect(change).toHaveBeenCalledWith('JFK');
  expect(input.props.style[0].backgroundColor).toBe('transparent');
});

test('Android waits for the native window color before mounting backdrop blur, and the edge follows the radius', async () => {
  mockNativeReady = false;
  const element = () => createElement(BlurTargetContext.Provider, { value: { current: null } }, createElement(Glass, { style: { width: 44, height: 44, borderRadius: 999 } }));
  await render(element());
  expect(hosts('BlurView')).toHaveLength(0);
  mockNativeReady = true;
  await act(async () => tree.update(element()));
  expect(hosts('BlurView')).toHaveLength(1);
  await act(async () => hosts('View')[0].props.onLayout({ nativeEvent: { layout: { width: 44, height: 44 } } }));
  expect(hosts('Path')[0].props.d).toMatch(/^M 22 0\.5 H 22 A/);
  expect(hosts('View')[0].props.style[0]).not.toHaveProperty('borderTopColor');
});

test('small Android controls retain glass styling without RenderEffect leaking rectangular corners', async () => {
  await render(createElement(BlurTargetContext.Provider, { value: { current: null } }, createElement(Button, { title: 'All flights', variant: 'secondary', selected: true })));
  expect(tree.root.findAllByType(Glass)).toHaveLength(1);
  expect(hosts('BlurView')).toHaveLength(0);
  await act(async () => hosts('View')[0].props.onLayout({ nativeEvent: { layout: { width: 100, height: 44 } } }));
  expect(hosts('Path')).toHaveLength(1);
  expect(hosts('Pressable')[0].props.style({ pressed: false })[0]).toMatchObject({ borderRadius: 999, overflow: 'hidden' });
});

test('supported iOS uses native Liquid Glass, unavailable APIs and reduce-transparency settings use a safe fallback', async () => {
  (Platform as { OS: string }).OS = 'ios';
  await render(createElement(Glass, { interactive: true }));
  expect(hosts('GlassView')[0].props).toMatchObject({ glassEffectStyle: 'regular', colorScheme: 'dark', isInteractive: true });
  jest.mocked(isGlassEffectAPIAvailable).mockReturnValue(false);
  await act(async () => tree.update(createElement(Glass, { interactive: false })));
  expect(hosts('GlassView')).toHaveLength(0);
  expect(hosts('BlurView')).toHaveLength(1);
  await act(async () => tree.unmount());
  jest.mocked(AccessibilityInfo.isReduceTransparencyEnabled).mockResolvedValue(true);
  await render(createElement(Glass));
  expect(hosts('BlurView')).toHaveLength(0);
  expect(hosts('GlassView')).toHaveLength(0);
  expect(hosts('View')[0].props.style[0].backgroundColor).toBe(palette.dark.surface);
});
