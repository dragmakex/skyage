import type { ExpoWebGLRenderingContext } from 'expo-gl';
import { Mesh, MeshBasicMaterial, Line, LineDashedMaterial, Scene, WebGLRenderer } from 'three';
import { toGeoJSON } from '../domain/arcs';
import type { AirportIndex, Flight } from '../domain/types';
import { palette } from '../ui/tokens';
import { createGlobeScene } from './scene';

// Exercise real geometry, projection and raycasting without requiring native GL in Jest.
jest.mock('three', () => ({
  ...jest.requireActual('three'),
  WebGLRenderer: jest.fn().mockImplementation(() => ({
    setSize: jest.fn(), setClearColor: jest.fn(), dispose: jest.fn(), debug: {},
    render: jest.fn((scene: { updateMatrixWorld: () => void }) => scene.updateMatrixWorld()),
  })),
}));

const index: AirportIndex = {
  AAA: { iata: 'AAA', name: 'Front', city: 'Front', country: '', lat: 0, lon: 0 },
  BBB: { iata: 'BBB', name: 'Back', city: 'Back', country: '', lat: 0, lon: 180 },
  CCC: { iata: 'CCC', name: 'West', city: 'West', country: '', lat: 0, lon: -15 },
  DDD: { iata: 'DDD', name: 'East', city: 'East', country: '', lat: 0, lon: 15 },
};
const flight = (from: string, to: string): Flight => ({ id: 'route', from, to, date: '2025-01-01', source: 'manual' });

function setup() {
  const gl = { drawingBufferWidth: 400, drawingBufferHeight: 600, endFrameEXP: jest.fn() } as unknown as ExpoWebGLRenderingContext;
  const runtime = createGlobeScene(gl, [0, 0]);
  runtime.resize(400, 600);
  return { runtime, gl };
}

test('front cities are pickable, back cities and routes are occluded, empty taps clear', () => {
  const { runtime, gl } = setup();
  runtime.update(toGeoJSON([flight('AAA', 'BBB')], index), palette.dark);
  runtime.render(1, false, true);
  expect(runtime.pick(200, 300)).toEqual({ iata: 'AAA' });
  expect(runtime.pick(0, 0)).toEqual({});
  runtime.update(toGeoJSON([flight('BBB', 'BBB')], index), palette.light);
  runtime.render(1, false, true);
  expect(runtime.pick(200, 300)).toEqual({});
  runtime.dispose();
  const frames = (gl.endFrameEXP as jest.Mock).mock.calls.length;
  runtime.render(1, false, true);
  expect(gl.endFrameEXP).toHaveBeenCalledTimes(frames);
});

test('route selection works after zoom and data replacement without city labels', () => {
  const { runtime } = setup();
  runtime.update(toGeoJSON([flight('CCC', 'DDD')], index, 'route'), palette.dark, 'route');
  runtime.render(1, false, true);
  expect(runtime.pick(200, 300)).toEqual({ id: 'route' });
  expect(runtime).not.toHaveProperty('labels');
  runtime.zoom(2.2);
  runtime.render(1, false, true);
  expect(runtime.pick(200, 300)).toEqual({ id: 'route' });
  runtime.update(toGeoJSON([], index), palette.light);
  runtime.render(1, false, true);
  expect(runtime.pick(200, 300)).toEqual({});
  runtime.dispose();
});

test('shader failures are recoverable errors rather than a silently blank first frame', () => {
  const { runtime } = setup();
  const renderer = (WebGLRenderer as unknown as jest.Mock).mock.results.at(-1)!.value;
  expect(() => renderer.debug.onShaderError()).toThrow('Globe shaders could not compile');
  runtime.dispose();
});

test.each(['light', 'dark'] as const)('%s globe texture has distinct land and country-border pixels', (mode) => {
  const { runtime } = setup();
  const renderer = (WebGLRenderer as unknown as jest.Mock).mock.results.at(-1)!.value;
  runtime.update(toGeoJSON([], index), palette[mode]);
  runtime.render(1, false, true);
  const scene: Scene = renderer.render.mock.calls.at(-1)[0];
  const globe = scene.children.find((o) => o instanceof Mesh && (o.material as MeshBasicMaterial).map) as Mesh;
  const pixels = (globe.material as MeshBasicMaterial).map!.image.data as Uint8Array;
  const colors = new Set<number>();
  for (let i = 0; i < pixels.length; i += 4) colors.add(pixels[i] * 65536 + pixels[i + 1] * 256 + pixels[i + 2]);
  expect(colors).toContain(parseInt(palette[mode].land.slice(1), 16));
  expect(colors).toContain(parseInt(palette[mode].countryBorder.slice(1), 16));
  expect(palette[mode].countryBorder).not.toBe(palette[mode].land);
  runtime.dispose();
});

test('past routes use light-blue tubes and upcoming routes keep their dashed style', () => {
  const { runtime } = setup();
  const renderer = (WebGLRenderer as unknown as jest.Mock).mock.results.at(-1)!.value;
  runtime.update(toGeoJSON([flight('CCC', 'DDD')], index), palette.dark);
  runtime.render(1, false, true);
  const scene: Scene = renderer.render.mock.calls.at(-1)[0];
  const tubes: Mesh[] = [];
  scene.traverse((object) => { if (object instanceof Mesh && object.userData.id === 'route') tubes.push(object); });
  expect(tubes).toHaveLength(1);
  expect(tubes[0].geometry.type).toBe('TubeGeometry');
  expect((tubes[0].material as MeshBasicMaterial).color.getHexString()).toBe('9bd8ff');
  runtime.update(toGeoJSON([{ ...flight('CCC', 'DDD'), date: '2099-01-01' }], index, undefined, '2026-01-01'), palette.dark);
  const dashed: Line[] = [];
  scene.traverse((object) => { if (object instanceof Line && object.material instanceof LineDashedMaterial) dashed.push(object); });
  expect(dashed).toHaveLength(1);
  expect(dashed[0].children).toHaveLength(0);
  runtime.dispose();
});
