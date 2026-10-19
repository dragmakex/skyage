# Skyage

A globe of every flight you have taken. Android, React Native (Expo).

The globe sits in the middle of the screen. Pinch to zoom from the sphere into a country
and its cities. Every flight is one line between two cities. A sheet slides up from the
bottom with the list, the stats and the detail of a selected flight.

Everything else (alerts, friends, backend, subscriptions) lives in `LATER.md`.

---

## 0. Design read

Reading this as: a personal flight visualization for one frequent flyer, with a sleek,
quiet, black zinc language with light-blue flight paths, leaning toward shadcn's token system for the sheet and
controls, and a Liquid Glass style treatment for the floating surfaces over a Three.js globe.

Dials: DESIGN_VARIANCE 4, MOTION_INTENSITY 4, VISUAL_DENSITY 3.

Two honesty notes, per the design skill:
- shadcn/ui is a web library. We adopt its token names, zinc values, radii and component
  conventions in React Native StyleSheet. If the team wants Tailwind classes, swap in
  React Native Reusables plus NativeWind, which is the community port of shadcn.
- The reference at https://liquid-glass.glassapp.dev/ is a DOM/WebGPU library, not a
  React Native library. Android uses a native approximation of its dark material:
  blur, translucent black tint, polished highlight edges, and a solid fallback. This
  does not reproduce the web renderer's optical refraction.

---

## 1. Scope

### v1
- Custom Three.js globe centered on screen, drag to spin, pinch to zoom. Bundled country
  geography, no flat-map transition, street tiles, or map-service account
- One great-circle line per flight between its two cities, city dots sized by visits,
  city labels once zoomed in
- Slide-up sheet with three heights: stats, list, detail
- Year filter in a floating glass capsule
- Add flight three ways, all ending at one review step: scan boarding pass (camera or a
  picture from the gallery), flight number plus date, route plus date
- Local storage only, JSON export and import, CSV import
- Always dark, including forms, settings, native dialogs and the globe

### Not now
Accounts, sync, push, live aircraft positions, friends, Pro, widgets, Wear OS, PDF
e-tickets, OCR of confirmation emails, airport screens.

---

## 2. Screens

Three routes, no tab bar: Globe, Add flight (modal), Settings.

### 2.1 Globe (home)

Layout
- The globe is centered between the top controls and the initial 20% drawer. It stays
  stationary as the glass sheet is pulled over it, so the globe is visible behind the glass.
- Launch camera fits the sphere to the viewport, centered on the mean of flight airport
  coordinates, or on 20°N 0°E with nothing stored.
- Idle: the globe rotates once every 120 s until the first touch, then never again this
  session. Reason: tells the user it is a sphere they can turn. Off under reduce motion.

Globe style (Three.js sphere with a bundled Natural Earth geography mask)
- Water and surrounding space use `background`, land uses `land`, country outlines use
  `border`. Geography is 1:110m, rasterized to 1024×512. Routes and airport dots use
  `flight` (#9BD8FF). No network map requests.
- No roads, state borders, general place labels, atmosphere, or terrain. Natural Earth
  public-domain credit appears in Settings.

Flight layers
- Lines: Three.js great circles, 96 segments each, just above the sphere. Past routes
  use light-blue tube geometry for high-DPI readability, opacity 0.85. Upcoming routes
  use dashed lines. With selection: selected route opacity 1, others 0.22.
- Cities: small sphere meshes sized by visit count, fill `flight`.
- Flight city labels: projected React Native Geist text in `mutedForeground`, shown
  when zoomed in, culled on the back of the sphere and when labels overlap.
- Tap a line to select it. Tap a city to list its flights. Tap empty globe to deselect.

Floating chrome (glass)
- Top: a capsule with the year chips, All plus every year that has flights, scrolling
  horizontally. Selected chip is `foreground` on `background`, others are text only.
- Top right: a glass circle with the settings icon.
- Nothing else floats. No FAB, no compass, no legend.

Empty: the globe idles, the sheet peek says "No flights yet" and holds the Add button.

### 2.2 Sheet

Always present, snap points 20%, 50%, 92%. Dark glass surface, 28 top radius, grabber
centered. The grabber can also be tapped to expand/collapse, including with a screen reader.

Peek (20% of available height)
- Left: stats in mono, "38 flights, 112,400 km, 41 airports", filtered by the year chips.
- Right: an "Add" pill button, `foreground` on `background`.

Half and full
- Flight rows grouped by year, newest first. Row: codes in mono with a thin rule between
  them, city names beneath in `mutedForeground`, date right. Flight number and airline on
  the second line when known.
- Rows are separated by hairlines, not cards. Year headers are sticky.

Selected flight
- The sheet swaps to detail: origin and destination codes at display size with city names
  under each, then rows: date, airline and flight number, distance, duration when times
  are known, seat. Two actions: "Show on globe" (camera eases to fit the line) and
  "Delete". A close button returns to the list. Selecting also flies the camera to the line.
- Selected city: the city name as title, "12 flights", its rows beneath.

### 2.3 Add flight (modal sheet)

Three option rows in shadcn list style, each with icon, title, one-line description:

1. Scan boarding pass. Camera with the barcode scanner (PDF417, Aztec, QR). "From gallery"
   picks a picture and scans it statically. The barcode is IATA BCBP, parsed offline in
   `bcbp.ts`: from, to, carrier, number, day of year, seat. BCBP has no year, so pick the
   year that puts the date closest to today and let the user confirm. No barcode found:
   "No barcode in that picture. Try a screenshot of the pass, or type the flight number."
2. Flight number. One field ("UA 123") and the native date picker. Lookup returns from, to
   and times. Several legs: list them. Nothing found: "No record for that flight on that
   date. Enter the route instead." Switches to step 3 with the number and date kept.
3. Route. From and to with airport autocomplete (IATA, city, name), date, optional flight
   number. Always works offline.

Review: from, to, date, airline, number, seat, all editable. Save. A duplicate asks once.

### 2.4 Settings

Appearance is always dark. Units (km, mi). Export (writes `skyage-flights.json`, share
sheet). Import (file picker, `.json` or `.csv`, shows "Found 112 flights, 3 already here",
then Import). Licenses and map attribution.

---

## 3. Data

```ts
type Flight = {
  id: string            // uuid
  date: string          // YYYY-MM-DD, local departure date
  from: string          // IATA
  to: string            // IATA
  airline?: string      // IATA carrier, "UA"
  number?: string       // "123"
  seat?: string
  depTime?: string      // ISO, when the lookup gave it
  arrTime?: string
  source: 'scan' | 'lookup' | 'manual' | 'import'
}
```

One JSON array under one key in `expo-sqlite/kv-store`. Read at launch, written on change.
ponytail: single blob; move to SQLite tables past a few thousand flights.

Airports resolve at render from `airports.json` (mwgg/Airports filtered to IATA codes at
build: iata, name, city, country, lat, lon). Distance is haversine, computed, never stored.

`arcs.ts` turns the flight array into two GeoJSON collections: lines with properties
`{ id, year, upcoming, selected }` and city points with `{ iata, city, visits }`. The
map re-renders from these; nothing else touches the map.

CSV import columns: `date,from,to,flight` with a header row, `flight` optional.

Flight number lookup: AeroDataBox via RapidAPI, `GET /flights/number/{carrier}{number}/{date}`.
Works for recent and upcoming dates, old dates often return nothing, hence the route step.
`EXPO_PUBLIC_AERODATABOX_KEY` is baked in at build time.
ponytail: the key ships in the APK, fine for a personal build; add a 30-line proxy before
a Play Store release.

---

## 4. Design

Tokens in `src/ui/tokens.ts`, shadcn names, zinc values. Nothing hard-codes a color,
radius or size elsewhere. The globe texture and materials use the same color values.

### Color

| Token | Dark | Use |
|---|---|---|
| background | #09090B | app surface, globe water, space |
| foreground | #FAFAFA | text, primary button |
| muted | #1C1C20 | pressed rows, skeletons |
| mutedForeground | #A1A1AA | secondary text, labels |
| border | #303742 | hairlines, country borders |
| land | #161C24 | globe land |
| ring | #71717A | focus |
| flight | #9BD8FF | flight paths, airport dots |
| surface | #111113 | inputs, secondary controls, glass tint |

The app is locked to the dark zinc palette; light palette values are not exposed as
an appearance option. UI controls stay neutral, with white primary buttons and black
text. The `flight` token is light blue (#9BD8FF) for globe routes and airport dots.
Inputs and secondary controls use the dark `surface` token (#111113).

Glass recipe: dark translucent surface, native blur intensity 48, 1 px white edge at
12%, top highlight at 30%, inner highlight at 12%, black shadow. Fallback without native
blur support or with reduced transparency: 98% dark surface, no blur. The shared globe
blur target is available to the bottom drawer, filters, settings button and Add modal.

### Type

Geist for UI, Geist Mono with tabular figures for codes, dates, numbers and stats. Fonts
load before the splash releases.

| Role | Size / line | Weight | Use |
|---|---|---|---|
| display | 32 / 36 | 600 | codes in detail |
| stat | 22 / 28 | 600 mono | peek stats |
| title | 18 / 24 | 600 | sheet titles, option rows |
| body | 14 / 20 | 400 | shadcn's `text-sm` default |
| caption | 12 / 16 | 400 | `mutedForeground` second lines |

No uppercase tracked labels anywhere. Supports font scale 1.3.

### Shape

Two systems with one rule. Glass surfaces are capsules (chips, buttons on the globe) or
28 at the sheet's top corners. Everything inside the sheet is shadcn: radius 12 for
inputs, option rows and buttons. No other radius exists.

### Elevation

Inside the sheet: none, hairlines only. The glass surfaces carry the one shadow from the
recipe. No card inside the sheet, ever.

### Components

Seven, in `src/ui/`: Glass (one wrapper around BlurView with the recipe and fallback),
Button (primary, secondary, ghost), Chip, Field, FlightRow, OptionRow, EmptyState.
The sheet is `@gorhom/bottom-sheet` with a Glass background component. Icons from
`phosphor-react-native`, regular weight.

### Motion

| Motion | Reason |
|---|---|
| idle globe rotation until first touch | shows it is a sphere |
| camera eases to the selected line | keeps context between list and globe |
| line opacity changes immediately | selection feedback |
| sheet snaps, library defaults | platform |

Under reduce motion: no idle rotation, camera jumps, opacity changes instantly. Nothing
else moves. No parallax, no springs on rows.

### Copy

No em-dashes. Commas between stats. Dates as "14 Oct 2026". Thousands separators in the
user's unit. No exclamation marks.

### States

Lookup: skeleton row while fetching, inline error with Retry. Scan: one-line hint while
searching. Import: count before commit. Globe: renders from the local array, no loading
state; bundled geography renders offline even on first launch. A GL initialization or
rendering failure shows an inline message while leaving the flight list usable.

---

## 5. Stack

| Need | Choice | Why |
|---|---|---|
| App | Expo SDK (latest stable), TypeScript, Expo Router | ships with the template |
| Globe | Three.js + expo-gl, bundled Natural Earth mask | custom globe, no map account or tokens; works with matching Expo Go |
| Sheet | @gorhom/bottom-sheet | snap points and gestures done right |
| Glass | expo-blur | blur where the platform samples it, fallback otherwise |
| Scan | expo-camera, live and `scanFromURLAsync` | PDF417, Aztec, QR on Android via ML Kit |
| Gallery | expo-image-picker | |
| Storage | expo-sqlite/kv-store | bundled, sync, one key |
| Export, import | expo-file-system, expo-sharing, expo-document-picker | |
| Date picker | @react-native-community/datetimepicker | native picker |
| Fonts | expo-font, Geist and Geist Mono | |
| Icons | phosphor-react-native | |
| Dates | `Intl` | no date library |
| Geo | `geo.ts`, haversine and great-circle points, 25 lines | no turf |
| Tests | Jest, expo preset | |

Not used: NativeWind and React Native Reusables (swap in if Tailwind classes are wanted),
Mapbox, MapLibre, Skia, react-query, zustand, any backend.

The globe needs OpenGL ES 3 support. There is no flat-map fallback; if rendering fails,
the local flight list remains usable. Rebuild existing native apps after the SDK swap.

Android: minSdk 26, edge to edge, predictive back. Measure GL rendering and glass blur
on real hardware; use the glass fallback if GL surfaces cannot be sampled or blur is slow.
Camera permission is requested when Scan is tapped, with one line of reason.

---

## 6. Layout

```
app/
  _layout.tsx          fonts, theme
  index.tsx            Globe and sheet
  add.tsx              Add flight modal
  settings.tsx
src/
  domain/              pure, one test each:
                       bcbp.ts geo.ts csv.ts stats.ts flightNumber.ts arcs.ts
  api/aerodatabox.ts   one fetch
  store.ts             load, save, add, remove
  map/                 GlobeView.tsx scene.ts geometry.ts earth-mask.json
  ui/                  tokens.ts and the seven components
  data/airports.json
```

---

## 7. Tests

- `bcbp.test.ts`: a real-shape BCBP string decodes to the right from, to, carrier,
  number, day and seat.
- `geo.test.ts`: SFO to DEN is about 1,550 km; the first and last arc points are the
  airports.
- `arcs.test.ts`: three flights give three line features and the right city visit counts;
  a selected id flags exactly one feature.
- `csv.test.ts`: header plus two lines yields two flights, a bad line is skipped.
- `stats.test.ts`: three flights through two airports gives 3, the summed distance, 2.
- `flightNumber.test.ts`: "UA 123", "ua123", "LH1234" split into carrier and number.

Manual before release: both themes, font scale 1.3, a boarding pass screenshot from an
airline app, a photographed paper pass, airplane mode on first launch, background/resume,
rotation, pinch, route/city picking, and a low-end device for GL support and glass frame rate.
`map/geometry.test.ts` covers spherical coordinates, texture orientation, antipodal routes,
route framing, and geography-mask integrity.

---

## 8. Pre-flight checklist

- [ ] zero em-dashes in UI strings
- [ ] black zinc surfaces everywhere, light-blue routes and airport dots
- [ ] two radius systems with the one rule, no card inside the sheet
- [ ] every number in mono with tabular figures
- [ ] glass has its solid fallback and is labeled an approximation in code
- [ ] the sphere fits the viewport above the sheet peek
- [ ] idle rotation stops on first touch and never runs under reduce motion
- [ ] Natural Earth credit in Settings, no map tokens or tile requests
- [ ] lookup and import have loading and error states
- [ ] dark appearance remains active with saved light/system preferences
- [ ] camera permission asked on Scan tap, with one line of reason
- [ ] export then import round-trips with zero duplicates

---

## 9. Open decisions

1. Custom Three.js globe selected; no map-service account required.
2. Light-blue flight paths and airport dots selected.
3. StyleSheet with shadcn tokens (assumed) or NativeWind plus React Native Reusables.
4. AeroDataBox (assumed) or AeroAPI for the flight number lookup.
5. App name "Skyage".
