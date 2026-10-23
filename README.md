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

Rebuild native development clients after installing new native modules. On iOS, native Liquid Glass requires iOS 26+, a compatible Xcode build, and the `expo-glass-effect` module; older versions use the frosted fallback. Android uses the supported frosted-glass approximation, not Apple's proprietary refraction.

Optional: copy `.env.example` to `.env` and set `EXPO_PUBLIC_AERODATABOX_KEY` for flight-number lookup. Manual Route entry, the globe, and boarding-pass parsing work without it. Public Expo keys ship in the app; use a proxy before distributing a shared API key.

## Checks

```sh
bun run test --runInBand
bun run typecheck
```

On a device: add a route, rotate and pinch the globe, tap a city or flight path, select a flight from the list and use Show on globe. Check year filtering, the 20%/50%/92% drawer (drag or tap its handle), both appearance modes, gray country borders in light mode, reduce motion, repeated Settings/Add navigation, background/resume, airplane mode, and export/import. Expand/collapse the drawer in both themes and check that no old background stays behind. Restart and confirm saved flights persist.

## Globe

- Three.js renders a centered globe, great-circle routes (dashed for upcoming flights), and matching airport dots. Dark is the default; light mode has distinct gray country borders. City names appear in the list and detail, never as labels on the globe.
- Drag rotates, pinch zooms. Idle rotation stops after the first interaction and is disabled with reduce motion. Each native surface owns one render loop; navigation blur and backgrounding stop/dispose it before Android detaches the surface. Returning creates a fresh context.
- Bundled Natural Earth 1:110m geography works offline, including on first launch. No map service, account, or tile requests. A native-image globe preview remains visible until the first successful GL frame and during recovery. Failed or missing surfaces retry up to three times, then expose a manual retry; returning to the screen also retries. The preview is static and does not show routes or support 3D interaction while GL is unavailable.
- This is not a street map: no roads, general place labels, or globe-to-flat transition. The 1024×512 geography mask is intentionally coarse at close zoom.
- Geography is public domain: https://www.naturalearthdata.com/about/terms-of-use/. To regenerate `src/map/earth-mask.json`, install Python's Pillow and run `python3 scripts/build-globe-map.py` (downloads Natural Earth source). Run `python3 scripts/build-globe-preview.py` afterward to regenerate the two theme-tinted preview layers from that mask; this step needs no network.

## Design

Dark-by-default zinc surfaces and Geist typography, with a saved light appearance option. Buttons, chips, form fields and menus share one Liquid Glass material. Icon buttons are circles; text buttons are pills. The glass drawer starts at 20% and expands over the stationary globe, with scrollable flight details. Its native surface is restored at the saved snap index on navigation return and app resume; transient hidden indices are ignored.

Supported iOS 26+ builds use Apple's native Liquid Glass through `expo-glass-effect`, gated by both native availability checks and Reduce Transparency. Android panels and older iOS use clipped native blur, tint and a specular edge that follows the rounded perimeter; small Android controls use the same tinted/specular material without RenderEffect, which otherwise leaks rectangular corners during transforms; there is no straight top highlight across circles. This is an approximation of Apple's material, not its optical refraction and not the unrelated web-only Glassapp library. Reduced-transparency settings get an opaque, readable surface.

The globe backdrop serves the drawer and Add modal; Settings has its own attached backdrop. On Android, `expo-system-ui` sets the actual native window color before blur mounts, so the blur library cannot capture a stale white `decorView.background` and leave a stationary pale slab under the moving sheet.

## Notes

- `bun install` applies `patches/three@0.166.1.patch`: Expo SDK 57 makes WebGL2 inherit from WebGL1, which Three.js r166 otherwise incorrectly rejects. Keep this patch until the renderer's context check supports that inheritance. `src/map/three-context.test.ts` protects the fix.
- Flights live on the device only. Settings has Export (JSON) and Import (JSON or CSV with `date,from,to,flight`).
- Blur is enabled only where the platform can sample an attached target safely; otherwise the shared material keeps its tinted, curved-edge fallback. Verify native Liquid Glass on iOS and GL/blur frame rate on real Android hardware before release. Tests cover missing/stale contexts, render failures, retries, navigation/background lifecycle, native window theme races, control actions, and glass fallbacks.
