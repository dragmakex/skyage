import type { ExpoWebGLRenderingContext } from 'expo-gl';
import type { LngLat } from '../domain/geo';
import type { Colors } from '../ui/tokens';
import { createGlobeScene, type GlobeData } from './scene';

type Input = { center: LngLat; data: GlobeData; colors: Colors; selectedId?: string; focus?: [LngLat, LngLat] };

// One native surface owns one scene and one RAF chain. A detached surface must never keep rendering.
export class GlobeRenderer {
  private scene: ReturnType<typeof createGlobeScene> | null = null;
  private gl: ExpoWebGLRenderingContext | null = null;
  private frame: number | undefined;
  private previous = 0;
  private lastProbe = 0;
  private size = { width: 1, height: 1 };
  private ready = false;

  constructor(
    private onReady: () => void,
    private onLost: (cause: unknown) => void,
    private motion: () => { idle: boolean; reduced: boolean },
  ) {}

  connect(gl: ExpoWebGLRenderingContext, input: Input) {
    this.stop();
    this.gl = gl;
    this.previous = 0;
    this.lastProbe = 0;
    try {
      this.checkContext();
      const scene = createGlobeScene(gl, input.center);
      this.scene = scene;
      scene.resize(this.size.width, this.size.height);
      scene.update(input.data, input.colors, input.selectedId);
      if (input.focus) scene.focus(...input.focus);
      this.draw(scene, 0);
    } catch (cause) { this.fail(cause); }
  }

  update(data: GlobeData, colors: Colors, selectedId?: string) {
    this.use((scene) => { this.checkContext(); scene.update(data, colors, selectedId); });
  }
  resize(width: number, height: number) {
    if (width <= 0 || height <= 0) return;
    this.size = { width, height };
    this.use((scene) => scene.resize(width, height));
  }
  focus(a: LngLat, b: LngLat) { this.use((scene) => scene.focus(a, b)); }
  drag(dx: number, dy: number) { this.use((scene) => scene.drag(dx, dy)); }
  zoom(ratio: number) { this.use((scene) => scene.zoom(ratio)); }
  pick(x: number, y: number): { iata?: string; id?: string } | undefined {
    let result: { iata?: string; id?: string } | undefined;
    this.use((scene) => { result = scene.pick(x, y); });
    return result;
  }

  stop() {
    if (this.frame !== undefined) cancelAnimationFrame(this.frame);
    this.frame = undefined;
    const scene = this.scene;
    this.scene = null;
    this.gl = null;
    this.ready = false;
    try { scene?.dispose(); }
    catch (cause) { console.warn('Globe surface cleanup failed', cause); }
  }

  private use(work: (scene: ReturnType<typeof createGlobeScene>) => void) {
    if (!this.scene) return;
    try { work(this.scene); }
    catch (cause) { this.fail(cause); }
  }
  private checkContext() {
    // Expo's isContextLost() always returns false, even after native surface destruction.
    if (!this.gl || this.gl.drawingBufferWidth <= 0 || this.gl.drawingBufferHeight <= 0 || typeof this.gl.getParameter(this.gl.VERSION) !== 'string') throw new Error('Globe surface is no longer available.');
  }
  private fail(cause: unknown) { this.stop(); this.onLost(cause); }
  private draw(scene: ReturnType<typeof createGlobeScene>, time: number) {
    if (this.scene !== scene) return;
    try {
      if (time - this.lastProbe >= 1000) { this.checkContext(); this.lastProbe = time; }
      const delta = this.previous ? Math.min((time - this.previous) / 1000, 0.05) : 0;
      this.previous = time;
      const motion = this.motion();
      scene.render(delta, motion.idle, motion.reduced);
      if (!this.ready) { this.ready = true; this.onReady(); }
      if (this.scene === scene) this.frame = requestAnimationFrame((next) => this.draw(scene, next));
    } catch (cause) { this.fail(cause); }
  }
}
