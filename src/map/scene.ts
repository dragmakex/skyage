import type { ExpoWebGLRenderingContext } from 'expo-gl';
import * as THREE from 'three';
import type { toGeoJSON } from '../domain/arcs';
import type { LngLat } from '../domain/geo';
import type { Colors } from '../ui/tokens';
import mask from './earth-mask.json';
import { globePoint, routePoints, routeView } from './geometry';

export type GlobeData = ReturnType<typeof toGeoJSON>;

function disposeObjects(group: THREE.Object3D) {
  const geometries = new Set<THREE.BufferGeometry>();
  const materials = new Set<THREE.Material>();
  group.traverse((object) => {
    if (object instanceof THREE.Mesh || object instanceof THREE.Line) {
      geometries.add(object.geometry);
      for (const material of Array.isArray(object.material) ? object.material : [object.material]) materials.add(material);
    }
  });
  geometries.forEach((g) => g.dispose());
  materials.forEach((m) => m.dispose());
  group.clear();
}

// shortcut: coarse 1:110m geography; increase the mask resolution when close-zoom detail matters.
function earthTexture(c: Colors) {
  const colors = [c.background, c.land, c.countryBorder].map((hex) => new THREE.Color(hex).getHex(THREE.SRGBColorSpace));
  const pixels = new Uint8Array(mask.width * mask.height * 4);
  let offset = 0;
  for (let i = 0; i < mask.runs.length; i += 2) {
    const color = colors[mask.runs[i]];
    for (let n = 0; n < mask.runs[i + 1]; n++) {
      pixels[offset++] = color >> 16 & 255;
      pixels[offset++] = color >> 8 & 255;
      pixels[offset++] = color & 255;
      pixels[offset++] = 255;
    }
  }
  const texture = new THREE.DataTexture(pixels, mask.width, mask.height);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.magFilter = THREE.LinearFilter;
  texture.minFilter = THREE.LinearFilter;
  texture.needsUpdate = true;
  return texture;
}

export function createGlobeScene(gl: ExpoWebGLRenderingContext, initialCenter: LngLat) {
  // Expo supplies GL but no DOM canvas. Three only needs this surface when a context is supplied.
  const canvas = {
    width: gl.drawingBufferWidth, height: gl.drawingBufferHeight, style: {},
    addEventListener() {}, removeEventListener() {}, setAttribute() {},
    getContext: () => gl,
  } as unknown as HTMLCanvasElement;
  if (!gl.canvas) Object.defineProperty(gl, 'canvas', { value: canvas, configurable: true });
  const renderer = new THREE.WebGLRenderer({ canvas, context: gl, antialias: true });
  renderer.debug.onShaderError = () => { throw new Error('Globe shaders could not compile for this surface.'); };
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(40, 1, 0.05, 100);
  const globe = new THREE.Mesh(new THREE.SphereGeometry(1, 96, 64).rotateY(-Math.PI / 2), new THREE.MeshBasicMaterial());
  const rim = new THREE.Mesh(globe.geometry, new THREE.MeshBasicMaterial({ side: THREE.BackSide, transparent: true, opacity: 0.4 }));
  rim.scale.setScalar(1.003);
  const routes = new THREE.Group();
  const cities = new THREE.Group();
  scene.add(globe, rim, routes, cities);
  let texture: THREE.DataTexture | undefined;
  let textureColors: Colors | undefined;
  let view = { lon: initialCenter[0], lat: initialCenter[1], distance: 3.4 };
  let target = { ...view };
  let width = 1, height = 1;
  let disposed = false;
  let autoFit = true;
  let bufferWidth = 0, bufferHeight = 0;

  function update(data: GlobeData, c: Colors, selectedId?: string) {
    disposeObjects(routes);
    disposeObjects(cities);
    if (textureColors !== c) {
      texture?.dispose();
      texture = earthTexture(c);
      textureColors = c;
      globe.material.map = texture;
      globe.material.needsUpdate = true;
    }
    renderer.setClearColor(c.background, 1);
    rim.material.color.set(c.border);
    for (const feature of data.lines.features) {
      const coords = (feature.geometry as GeoJSON.LineString).coordinates;
      const points = routePoints(coords[0] as LngLat, coords[coords.length - 1] as LngLat);
      const selected = feature.properties?.selected;
      const dashed = feature.properties?.upcoming && !selected;
      const opacity = selected ? 1 : selectedId ? 0.22 : 0.85;
      const material = dashed
        ? new THREE.LineDashedMaterial({ color: c.flight, dashSize: 0.025, gapSize: 0.018, transparent: true, opacity, depthWrite: false })
        : new THREE.LineBasicMaterial({ transparent: true, opacity: 0, depthWrite: false });
      const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), material);
      line.computeLineDistances();
      line.userData = { id: String(feature.properties?.id) };
      if (!dashed) {
        // Tubes stay readable on high-DPI Android; native GL line width is fixed to one pixel.
        const tube = new THREE.Mesh(
          new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points), 96, selected ? 0.003 : 0.0018, 5, false),
          new THREE.MeshBasicMaterial({ color: c.flight, transparent: true, opacity, depthWrite: false }),
        );
        tube.userData = line.userData;
        line.add(tube);
      }
      routes.add(line);
    }
    const dotGeometry = new THREE.SphereGeometry(1, 10, 8);
    const dotMaterial = new THREE.MeshBasicMaterial({ color: c.flight });
    for (const feature of data.cities.features) {
      const point = globePoint((feature.geometry as GeoJSON.Point).coordinates as LngLat, 1.009);
      const dot = new THREE.Mesh(dotGeometry, dotMaterial);
      dot.position.copy(point);
      dot.scale.setScalar(Math.min(0.018, 0.007 + Number(feature.properties?.visits) * 0.0006));
      dot.userData = { iata: String(feature.properties?.iata) };
      cities.add(dot);
    }
    if (!cities.children.length) { dotGeometry.dispose(); dotMaterial.dispose(); }
  }

  function render(delta: number, idle: boolean, reduceMotion: boolean) {
    if (disposed) return;
    if (bufferWidth !== gl.drawingBufferWidth || bufferHeight !== gl.drawingBufferHeight) {
      bufferWidth = gl.drawingBufferWidth; bufferHeight = gl.drawingBufferHeight;
      renderer.setSize(bufferWidth, bufferHeight, false);
    }
    if (idle) target.lon += delta * 3;
    const blend = reduceMotion ? 1 : 1 - Math.exp(-delta * 7);
    view.lon += (target.lon - view.lon) * blend;
    view.lat += (target.lat - view.lat) * blend;
    view.distance += (target.distance - view.distance) * blend;
    camera.position.copy(globePoint([view.lon, Math.max(-85, Math.min(85, view.lat))], view.distance));
    camera.lookAt(0, 0, 0);
    camera.updateMatrixWorld();
    renderer.render(scene, camera);
    gl.endFrameEXP();
  }

  return {
    update,
    render,
    resize(w: number, h: number) {
      width = Math.max(1, w); height = Math.max(1, h);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      if (autoFit) {
        target.distance = 1 / Math.sin(Math.atan(Math.tan(20 * Math.PI / 180) * Math.min(1, camera.aspect) * 0.88));
        view.distance = target.distance;
      }
    },
    drag(dx: number, dy: number) {
      target.lon -= dx / width * 180;
      target.lat = Math.max(-85, Math.min(85, target.lat + dy / height * 120));
      view = { ...target };
    },
    zoom(ratio: number) {
      autoFit = false;
      target.distance = Math.max(1.3, Math.min(8, 1 + (target.distance - 1) / ratio));
      view = { ...target };
    },
    focus(a: LngLat, b: LngLat) {
      autoFit = false;
      const next = routeView(routePoints(a, b), width / height);
      next.lon = view.lon + ((next.lon - view.lon + 540) % 360 + 360) % 360 - 180;
      target = next;
    },
    pick(x: number, y: number): { iata?: string; id?: string } {
      const ray = new THREE.Raycaster();
      ray.setFromCamera(new THREE.Vector2(x / width * 2 - 1, 1 - y / height * 2), camera);
      ray.params.Line = { threshold: (view.distance - 1) * 0.012 };
      const surface = ray.intersectObject(globe)[0]?.distance ?? Infinity;
      const dots = ray.intersectObjects(cities.children).filter((hit) => hit.distance <= surface + 0.025);
      if (dots[0]) return dots[0].object.userData;
      const lines = ray.intersectObjects(routes.children).filter((hit) => hit.distance <= surface + 0.025);
      return lines[0]?.object.userData ?? {};
    },
    dispose() {
      disposed = true;
      texture?.dispose();
      disposeObjects(scene);
      renderer.dispose();
    },
  };
}
