import { createElement } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import { GlobePreview } from './GlobePreview';

jest.mock('react-native', () => ({ View: 'View', Image: 'Image', StyleSheet: { absoluteFill: { position: 'absolute' }, hairlineWidth: 1 } }));
jest.mock('../ui/theme', () => ({ useTheme: () => ({ c: require('../ui/tokens').palette.light }) }));
jest.mock('../../assets/globe-land.png', () => 1);
jest.mock('../../assets/globe-borders.png', () => 2);
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

test('the always-visible preview fits its globe, never the PNG intrinsic size, including during recovery', async () => {
  const original = console.error;
  const warning = jest.spyOn(console, 'error').mockImplementation((...args) => {
    if (String(args[0]).startsWith('react-test-renderer is deprecated')) return;
    original(...args);
  });
  let tree!: ReactTestRenderer;
  try {
    await act(async () => { tree = create(createElement(GlobePreview, { diameter: 320 })); });
    const images = () => tree.root.findAll((node) => String(node.type) === 'Image');
    expect(images()).toHaveLength(2);
    for (const image of images()) expect(image.props.style[1]).toMatchObject({ width: 320, height: 320 });
    await act(async () => tree.update(createElement(GlobePreview, { diameter: 240 })));
    for (const image of images()) expect(image.props.style[1]).toMatchObject({ width: 240, height: 240 });
    expect(tree.root.findAll((node) => String(node.type) === 'View')[1].props.style).toMatchObject({ width: 240, height: 240, borderRadius: 120, overflow: 'hidden' });
  } finally { if (tree) await act(async () => tree.unmount()); warning.mockRestore(); }
});
