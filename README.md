# Skyage

A globe of every flight you have taken. Android, React Native (Expo). See `SPEC.md`.

## Run

```sh
bun install
bun run start
```

Open with Expo Go matching this project's Expo SDK, on an Android device or emulator. The globe uses Three.js and `expo-gl`, both without Mapbox or map tokens. If Expo Go for this SDK is unavailable, install Android Studio, start an emulator (or connect a USB-debugging phone), and build locally:

```sh
bun run android
```

Existing native development builds must be rebuilt after switching from Mapbox to `expo-gl`.

Optional: copy `.env.example` to `.env` and set `EXPO_PUBLIC_AERODATABOX_KEY` for flight-number lookup. Manual Route entry, the globe, and boarding-pass parsing work without it. Public Expo keys ship in the app; use a proxy before distributing a shared API key.

## Checks

```sh
bun run test --runInBand
bun run typecheck
```

On a device: add a route, rotate and pinch the globe, tap a city or flight path, select a flight from the list and use Show on globe. Check year filtering, the 20%/50%/92% drawer (drag or tap its handle), dark appearance with the phone in light mode, reduce motion, background/resume, airplane mode, and export/import. Restart and confirm saved flights persist.

## Globe

- Three.js renders a centered dark globe, light-blue great-circle routes (dashed for upcoming flights), and matching airport dots. Flight cities get Geist labels when zoomed in.
- Drag rotates, pinch zooms. Idle rotation stops after the first interaction and is disabled with reduce motion. Rendering pauses in the background.
- Bundled Natural Earth 1:110m geography works offline, including on first launch. No map service, account, or tile requests.
- This is not a street map: no roads, general place labels, or globe-to-flat transition. The 1024×512 geography mask is intentionally coarse at close zoom.
- Geography is public domain: https://www.naturalearthdata.com/about/terms-of-use/. To regenerate `src/map/earth-mask.json`, install Python's Pillow and run `python3 scripts/build-globe-map.py` (downloads Natural Earth source).

## Design

Always-dark zinc surfaces and Geist typography, inspired by [shadcn/ui](https://ui.shadcn.com/). The glass drawer starts at 20% and expands over the stationary globe.

[Glassapp Liquid Glass](https://liquid-glass.glassapp.dev/) is a DOM/WebGPU library and explicitly does not support native ports. This Android app uses a **native approximation**, not that package: dark tint, native blur, specular edges, and a solid fallback. It does not reproduce the reference's optical refraction. The shared globe blur target also serves the Add modal. Reduced-transparency settings on iOS disable blur.

## Notes

- `bun install` applies `patches/three@0.166.1.patch`: Expo SDK 57 makes WebGL2 inherit from WebGL1, which Three.js r166 otherwise incorrectly rejects. Keep this patch until the renderer's context check supports that inheritance. `src/map/three-context.test.ts` protects the fix.
- Flights live on the device only. Settings has Export (JSON) and Import (JSON or CSV with `date,from,to,flight`).
- Glass surfaces use blur where the platform can sample it. GL surface blur and frame rate need checking on real Android hardware; set `GLASS_BLUR` to `false` in `src/ui/tokens.ts` for the translucent fallback.
