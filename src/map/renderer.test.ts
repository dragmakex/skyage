import type { ExpoWebGLRenderingContext } from 'expo-gl';
import { toGeoJSON } from '../domain/arcs';
import { palette } from '../ui/tokens';
import { GlobeRenderer } from './renderer';
import { createGlobeScene } from './scene';

jest.mock('./scene', () => ({ createGlobeScene: jest.fn() }));
const data = toGeoJSON([], {});
const input = { center: [0, 20] as [number, number], data, colors: palette.dark };
const originalRequest = globalThis.requestAnimationFrame;
const originalCancel = globalThis.cancelAnimationFrame;
let frames: Map<number, FrameRequestCallback>;
let nextFrame: number;
function scene() {
  return { render: jest.fn(), update: jest.fn(), resize: jest.fn(), focus: jest.fn(), drag: jest.fn(), zoom: jest.fn(), pick: jest.fn(() => ({ iata: 'LHR' })), dispose: jest.fn() };
}
function context(version: string | undefined = 'OpenGL ES 3.0') {
  return { VERSION: 0x1f02, drawingBufferWidth: 400, drawingBufferHeight: 600, getParameter: jest.fn(() => version) } as unknown as ExpoWebGLRenderingContext;
}
function tick(time: number) {
  const [id, callback] = frames.entries().next().value!;
  frames.delete(id); callback(time);
}

beforeEach(() => {
  frames = new Map(); nextFrame = 0;
  globalThis.requestAnimationFrame = jest.fn((callback) => { frames.set(++nextFrame, callback); return nextFrame; });
  globalThis.cancelAnimationFrame = jest.fn((id) => { if (id != null) frames.delete(id); });
  jest.mocked(createGlobeScene).mockReset();
});
afterAll(() => { globalThis.requestAnimationFrame = originalRequest; globalThis.cancelAnimationFrame = originalCancel; });

function setup() {
  const ready = jest.fn(), lost = jest.fn(), motion = jest.fn(() => ({ idle: true, reduced: false }));
  const renderer = new GlobeRenderer(ready, lost, motion);
  const runtime = scene();
  jest.mocked(createGlobeScene).mockReturnValue(runtime);
  renderer.resize(400, 600);
  return { renderer, runtime, ready, lost, motion };
}

test('first frame is rendered before ready; blur/unmount stops the sole RAF chain', () => {
  const { renderer, runtime, ready, lost } = setup();
  renderer.connect(context(), input);
  expect(runtime.resize).toHaveBeenCalledWith(400, 600);
  expect(runtime.update).toHaveBeenCalledWith(data, palette.dark, undefined);
  expect(runtime.render).toHaveBeenCalledTimes(1);
  expect(ready).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(1);
  const stale = [...frames.values()][0];
  renderer.stop();
  expect(frames.size).toBe(0);
  expect(runtime.dispose).toHaveBeenCalledTimes(1);
  stale(100);
  expect(runtime.render).toHaveBeenCalledTimes(1);
  expect(lost).not.toHaveBeenCalled();
});

test('a native context returning undefined is rejected and a subsequent context recovers', () => {
  const { renderer, ready, lost } = setup();
  const invalid = context();
  jest.mocked(invalid.getParameter).mockReturnValue(undefined);
  renderer.connect(invalid, input);
  expect(createGlobeScene).not.toHaveBeenCalled();
  expect(ready).not.toHaveBeenCalled();
  expect(lost).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(0);
  renderer.connect(context(), input);
  expect(ready).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(1);
  renderer.stop();
});

test('zero-sized buffers and failed initialization never mark an empty globe ready', () => {
  const { renderer, ready, lost } = setup();
  const zero = context();
  Object.defineProperty(zero, 'drawingBufferWidth', { value: 0 });
  renderer.connect(zero, input);
  jest.mocked(createGlobeScene).mockImplementationOnce(() => { throw new Error('GPU unavailable'); });
  renderer.connect(context(), input);
  expect(ready).not.toHaveBeenCalled();
  expect(lost).toHaveBeenCalledTimes(2);
  expect(frames.size).toBe(0);
});

test('silent native context loss is detected without relying on Expo isContextLost', () => {
  const { renderer, runtime, lost } = setup();
  const gl = context();
  renderer.connect(gl, input);
  jest.mocked(gl.getParameter).mockReturnValue(undefined);
  tick(1001);
  expect(runtime.render).toHaveBeenCalledTimes(1);
  expect(runtime.dispose).toHaveBeenCalledTimes(1);
  expect(lost).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(0);
});

test('render and theme/data update errors cleanly release the old surface', () => {
  const { renderer, runtime, lost } = setup();
  renderer.connect(context(), input);
  runtime.render.mockImplementationOnce(() => { throw new Error('trim of undefined'); });
  tick(16);
  expect(frames.size).toBe(0);
  const replacement = scene();
  jest.mocked(createGlobeScene).mockReturnValue(replacement);
  renderer.connect(context(), input);
  replacement.update.mockImplementationOnce(() => { throw new Error('lost surface'); });
  renderer.update(data, palette.light);
  expect(lost).toHaveBeenCalledTimes(2);
  expect(replacement.dispose).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(0);
});

test('a stale callback cannot render or schedule work on a replacement scene', () => {
  const { renderer, runtime } = setup();
  renderer.connect(context(), input);
  const stale = [...frames.values()][0];
  const replacement = scene();
  jest.mocked(createGlobeScene).mockReturnValue(replacement);
  renderer.connect(context(), input);
  stale(16);
  expect(runtime.render).toHaveBeenCalledTimes(1);
  expect(replacement.render).toHaveBeenCalledTimes(1);
  expect(frames.size).toBe(1);
  renderer.stop();
});

test('motion preferences, camera actions and picking stay connected to the live scene', () => {
  const { renderer, runtime, motion } = setup();
  renderer.connect(context(), { ...input, focus: [[0, 0], [10, 10]] });
  motion.mockReturnValue({ idle: false, reduced: true });
  tick(100); tick(500);
  expect(runtime.render).toHaveBeenLastCalledWith(0.05, false, true);
  renderer.drag(12, 4); renderer.zoom(2);
  expect(runtime.drag).toHaveBeenCalledWith(12, 4);
  expect(runtime.zoom).toHaveBeenCalledWith(2);
  expect(renderer.pick(200, 300)).toEqual({ iata: 'LHR' });
  expect(runtime.focus).toHaveBeenCalledWith([0, 0], [10, 10]);
  renderer.stop();
});
