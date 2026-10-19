import { WebGLRenderer } from 'three';

// Expo SDK 57's WebGL2 prototype inherits from WebGL1, unlike browser contexts.
test('Three accepts Expo-style WebGL2 inheritance but still rejects WebGL1', () => {
  class WebGL1 {}
  const reachedContext = new Error('context accepted');
  class WebGL2 extends WebGL1 {
    getContextAttributes() { throw reachedContext; }
  }
  const globals = globalThis as unknown as Record<string, unknown>;
  const names = ['WebGLRenderingContext', 'WebGL2RenderingContext'];
  const original = names.map((name) => Object.getOwnPropertyDescriptor(globalThis, name));
  try {
    globals.WebGLRenderingContext = WebGL1;
    globals.WebGL2RenderingContext = WebGL2;
    expect(() => new WebGLRenderer({ canvas: {} as HTMLCanvasElement, context: new WebGL2() as unknown as WebGL2RenderingContext })).toThrow(reachedContext);
    expect(() => new WebGLRenderer({ canvas: {} as HTMLCanvasElement, context: new WebGL1() as WebGLRenderingContext })).toThrow('WebGL 1 is not supported');
  } finally {
    names.forEach((name, i) => {
      if (original[i]) Object.defineProperty(globalThis, name, original[i]!);
      else delete globals[name];
    });
  }
});
