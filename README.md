# Skyage

A native Android app for tracking flights: open it and see every flight you follow on a live world
globe, with a slidable Liquid Glass sheet holding the list.

## What it does

- **Globe root screen** — hand-built orthographic projection: pan, pinch, double-tap to zoom, live
  aircraft drawn where they actually are. No map SDK, no tile server.
- **Follow a flight** by IATA number (`UA123`) plus origin/destination airports; the app maps the
  airline to its ICAO callsign (`UAL123`) and matches it against a live ADS-B snapshot.
- **Progress, altitude, speed, remaining distance** along the great circle, with a
  **route-unknown** state when you skip the airports and an **off-route** warning when the matched
  aircraft is more than 400 km off your line (a callsign is not route-unique).
- **Liquid Glass UI** — real backdrop blur (Haze) plus a four-pass edge treatment: tint, refraction
  band, specular rim, drop shadow.
- Flights persist locally; refreshes poll every 60 s while the app is in front.

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.1.10, single-activity, `minSdk 26` / `targetSdk 35` |
| UI | Jetpack Compose (BOM 2025.06.00), Material 3, no navigation library — a sealed `Screen` type |
| State | `ViewModel` + `StateFlow`, manual DI, no Hilt |
| Glass | [Haze](https://github.com/chrisbanes/haze) 1.6.10 for blur, hand-rolled rim/refraction drawing in `ui/Glass.kt` |
| Maps | None. `geo/Globe.kt` (orthographic) + `ui/FlightMap.kt` (Web Mercator), pure Canvas + math |
| Network | `HttpURLConnection` + `org.json`, no Retrofit/OkHttp |
| Storage | One JSON file via `FlightStore`; datasets are `|`-delimited assets parsed in ~8 ms |
| Build | AGP 8.9.1, Gradle 8.13 |

## Data

- **Live positions:** [OpenSky Network](https://opensky-network.org) REST API, anonymous access
  (400 credits/day, one global snapshot cached for 60 s). See `docs/DATA-SOURCES.md`.
- **Airports:** [OurAirports](https://ourairports.com/data/) (public domain), ~9,000 with IATA codes.
- **Airlines:** [Wikidata](https://www.wikidata.org) (CC0), ~1,100 with IATA→ICAO pairs.
- Airline logos are **not** included: the IATA code is rendered as a badge instead.

## Build

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # geo, globe and dataset-parser tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and `local.properties` pointing at your SDK (`sdk.dir=...`). Regenerating the
datasets needs Python 3 and network access — the commands live in `docs/DATA-SOURCES.md`.

## Status

Working prototype, debug-signed only. Missing for production: a schedule data source (routes are
typed by hand), release signing config, a backend proxy so the OpenSky quota isn't per-device, and
tests for the UI layer.
