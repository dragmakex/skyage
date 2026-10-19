# Skyage

Android-first flight tracker in the Flighty class, built with React Native (Expo).
Your flights, live, with alerts before the airline tells you, and the people you fly with.

This file is the v1 contract. Change it before changing the code.

---

## 0. Design read

Reading this as: premium consumer utility for frequent flyers on Android, with a calm,
numbers-first language, leaning toward Material 3 structure (navigation bar, sheets,
predictive back, native pickers) under a custom skin: Geist type, cold neutral surfaces,
one cobalt accent, semantic status colors, no card-in-card.

Dials: DESIGN_VARIANCE 5, MOTION_INTENSITY 5, VISUAL_DENSITY 6.
This is product UI, not a landing page. Variance above 5 hurts scanability at 05:00 in a
terminal. Density 6 because travel day is data-dense; whitespace comes from grouping,
not from hiding data.

The taste skill marks native mobile out of scope. What transfers: anti-default discipline,
type and color locks, state coverage (loading, empty, error, stale), reduced motion,
the AI-tell bans. Platform conventions come from Material 3 and the Android guidelines.

---

## 1. Scope

### v1 (ship)
- Flights list with travel-day mode
- Add flight: flight number + date, or route + date; calendar import
- Flight detail: status, timeline, gates and belt, inbound aircraft, aircraft, connection,
  route performance, weather, notes, airline links, your history on the route
- Live map: collapsing header on detail, full map on Today
- Push alerts, 20 types, all server-driven
- Travel-day ongoing notification (Android 16 Live Update where available)
- Logbook: lifetime and per-year stats, routes map
- Friends: invite link, shared flights, Today map
- Airport screen: advisories, weather, delay trend
- Free / Pro via Google Play Billing, first flight gets Pro free

### v1.1
Home-screen widgets, email-forward import, TripIt import, gate prediction from our own
history, shareable year recap, Wear OS tile, fast-lane data for connections.

### v2
ML delay prediction trained on our polled history, Family plan, Business/team.

### Not building
iOS, web app, public profiles, feeds, chat, airport terminal maps, seat maps.

### Feature map against Flighty

| Flighty | Skyage v1 | Note |
|---|---|---|
| Live tracking | Flights, Flight detail | |
| 65+ alerts | 20 alert types | section 4 |
| ML delay predictions | Inbound-aircraft heuristic | ML in v2 |
| Where's My Plane | Inbound section | |
| Connection Assistant | Connection section | rule-based, no fast-lane data yet |
| Flighty Friends, Today | Friends, Today | invite links, share sheet for non-users |
| Passport | Logbook | one client-side reduce |
| Flighty Airports | Airport screen | FAA advisories are US only |
| Live Activities, Dynamic Island | Android 16 Live Update notification | ongoing notification below 16 |
| Widgets | v1.1 | needs a native module |
| Apple Watch | Wear OS, v1.1 | |
| Calendar import (Pro) | v1, free | client-side, zero marginal cost |
| Email, TripIt import | v1.1 | |
| Gate predictions | v1.1 | |
| Morning Of briefing | Travel-day briefing alert | |
| Pro free on first flight | same | |

Do not use Flighty's feature names (Passport, Where's My Plane, Flighty Friends,
Connection Assistant) in UI or the store listing.

---

## 2. Navigation

Material 3 navigation bar, four destinations:

| Tab | Purpose |
|---|---|
| Flights | upcoming and recent flights, travel-day hero |
| Today | map of everything in the air today, yours and friends' |
| Friends | people, invites, sharing controls |
| Logbook | lifetime stats, year filters, routes map |

Pushed screens: Flight detail, Airport, Add flight, Settings (from the avatar on Flights),
Paywall. Predictive back on. Edge-to-edge with safe-area insets.

---

## 3. Screens

### 3.1 Flights (home)

- Header: "Flights", avatar top-right opens Settings.
- Travel-day hero: when a flight departs within 24 h or is in the air, the first row becomes
  a large card: status sentence ("Boards in 1 h 12 min"), gate, RouteLine with live plane.
  It replaces the normal row for that flight, it does not stack on it.
- Upcoming: one FlightCard per flight, grouped under date labels. Same-day legs sit under
  one date label with a connection row between them ("1 h 35 min in DEN · Tight").
- Past: collapsed "Past flights" group showing the last three, link to Logbook.
- Extended FAB "Add flight" (pill, accent).
- Swipe a row to remove, snackbar with Undo.
- Empty: "No flights yet" / "Add one by flight number or from your calendar." One button:
  Add flight.
- Loading: three FlightCard skeletons. Error: inline banner with Retry above cached rows.

FlightCard anatomy:

```
UA 123  United                              On time     ident mono, airline, StatusPill
SFO ─────────────✈─────────────── DEN                  codes at display size, plane only in air
08:40   Gate B12                 12:05   Term B         mono times, struck scheduled when estimate differs
```

### 3.2 Add flight

- Segmented control: Flight number | Route. Date uses the native Android date picker.
- Flight number: airline autocomplete from the bundled airline list plus number. Results
  list every leg matching that ident on that date (same ident can fly twice).
- Route: origin and destination autocomplete from the bundled airport list, date, results.
- "Import from calendar" button: requests calendar permission, scans the next 90 days,
  matches flight-number patterns and airline names, shows candidates with checkboxes.
  Pure function `calendarParse.ts`, one test.
- Past dates allowed (feeds Logbook). Free: 12 months back. Pro: unlimited.
- Adding writes a trip row; the server polls that flight immediately.

### 3.3 Flight detail

- Collapsing map header: great-circle polyline, origin and destination markers, plane
  marker when in air. Tap expands to full screen.
- Sticky header on collapse: ident, StatusPill. Bell icon opens a sheet listing every
  alert sent for this flight, so the user can see what happened while they slept.
- Sections, separated by hairlines and spacing, never nested cards:

1. Status sentence at display size: "Departs in 2 h 10 min", "In the air, lands 14:02",
   "Landed 12 min early", "Cancelled".
2. Timeline: two columns, Depart and Arrive. Rows: Scheduled, Estimated, Actual. Actual
   appears when known and the estimate is struck. Delta chips colored by status
   ("25 min late"). Totals row: air time against block time.
3. At the airport: terminal, gate, baggage belt, check-in link (airline deep link).
   DataRow, mono values right-aligned.
4. Inbound (Pro): "Your aircraft is coming from LAX", mini RouteLine for the inbound leg,
   its status, predicted impact ("Likely 20 min late to your gate").
5. Connection (Pro, only when a next leg exists at the same airport within 24 h): layover
   duration, risk pill, checklist rows: passport control, bag recheck, terminal change
   and how (walk, tram, bus), minimum time we assume.
6. Aircraft: type for everyone. Pro adds registration, age, first flight date.
7. Route performance (Pro): last 60 days of this ident as one stacked horizontal bar,
   early / on time / late / 45+ late / cancelled, no background track, with one sentence
   ("Usually on time", "Often late").
8. Weather at both airports: METAR decoded to one line ("Clear, 18°C, wind 9 kt"), raw
   METAR behind a disclosure.
9. My notes: confirmation code, seat, fare class. Inline editable. Never shared.
10. Airline: call, website, status page.
11. Your history on this route: "You've flown SFO-DEN 6 times, 14 h 20 min."
12. Share with friends toggle, on by default once the user has a friend.
13. Remove flight.

Free users see sections 4, 5 and 7 as locked rows with the Pro label; tapping opens Paywall.

### 3.4 Today

- Full-screen map, dark style in dark mode. Shows every flight (yours and friends') that
  departs within 24 h or is in the air. Your plane in accent, friends' in text color with
  an avatar badge.
- Bottom sheet lists today's flights as compact rows: avatar, "You" or name, ident,
  status, time. Tap focuses the map, tap again opens detail (friend flights open read-only).
- Empty: map centered on home airport, sheet says "Nothing in the air today" with the next
  flight row.
- Positions refresh every 60 s while this screen or a detail screen is visible, never in
  the background.

### 3.5 Friends

- List of friends, each with their next flight as a compact row ("Next: DL 455, Sat").
- Invite: generates a link (`skyage.app/f/<code>`), Android App Link opens the app.
  Pending requests section with Accept / Ignore.
- Per friend: mute their alerts, stop sharing with them, remove.
- A friend sees: ident, route, times, status, position. Never confirmation, seat or notes.
- Sharing with someone who does not have the app: Android share sheet with a text summary
  ("UA 123 SFO-DEN, departs 08:40, on time"). No web page in v1.

### 3.6 Logbook

- Lifetime tiles in plain layout with hairlines, no cards: flights, distance, air time,
  countries, airports, airlines. Mono numbers at headline size.
- Year chips: All, 2026, 2025, and so on.
- DataRows: top routes, top airlines, top aircraft types, longest flight, on-time rate,
  hours lost to delays. Tap a route to list those flights.
- Collapsible header: non-interactive lite-mode map with every route flown as polylines.
- "Add past flight" opens Add flight with a past date.
- Computed on the client in `logbook.ts` from the trips cache. One reduce, one test.

### 3.7 Airport

- Header: IATA at display size, name, city, local time, one-line weather.
- Advisories: FAA ground stops, ground delay programs, closures, with duration and the
  advisory text. Non-US airports: "No advisory source for this airport yet."
- Delay trend (Pro): average departure delay over the last 6 h, from flights we are already
  polling at that airport. "Departures running 22 min late on average" or "Running on time."
- Your flights from here.
- Link to the airport website. No terminal maps.

### 3.8 Settings

- Account: name, photo, home airport, nationality (asked lazily, the first time a
  connection needs it), sign out, export data, delete account.
- Appearance: theme (system, light, dark), 12 h / 24 h (defaults to system), km / mi.
- Alerts: per-type toggles grouped Free and Pro. Four Android notification channels,
  Status, Gates, Predictions, Friends, so system settings mirror ours.
- Import: calendar on/off.
- Subscription: plan, manage in Play, restore.
- About, privacy policy, terms, licenses.

### 3.9 Paywall

One screen. Headline "Know before the airline tells you." Five rows of what Pro adds.
Two plan pills (Yearly marked as better value, Monthly). One primary button. Restore link.
Shown when a locked row is tapped or when the free first flight lands.

---

## 4. Alerts

All alerts originate on the server from a diff between two polls. The app never polls in
the background. Notification title names the flight and the change; body gives reason and
new value. No exclamation marks.

| Alert | Trigger | Tier | Channel |
|---|---|---|---|
| Travel-day briefing | 06:00 local on departure day: time, gate, status, leave-by | Free | Status |
| Delay | estimated departure moves 15 min or more | Free | Status |
| Early delay warning | inbound heuristic predicts 15+ min before airline posts | Pro | Predictions |
| Delay reason | ATC / weather / airport cause attached to a delay | Pro | Predictions |
| Schedule change | scheduled time changes more than 24 h out | Free | Status |
| Cancelled | | Free | Status |
| Diverted | | Free | Status |
| Gate assigned | first gate for departure or arrival | Free | Gates |
| Gate change | | Free | Gates |
| Terminal change | | Free | Gates |
| Baggage belt | belt assigned | Pro | Gates |
| Check-in open | 24 h before, deep links to airline | Pro | Status |
| Boarding soon | 40 min before estimated departure | Free | Status |
| Departed | off-block or take-off | Free | Status |
| Landed | with minutes early or late against schedule | Free | Status |
| Aircraft swap | registration or type changes | Pro | Predictions |
| Inbound departed / landed | the aircraft that becomes yours | Pro | Predictions |
| Airport advisory | ground stop or GDP at origin or destination within 12 h | Pro | Predictions |
| Connection at risk | layover drops below threshold after an estimate change | Pro | Predictions |
| Friend activity | friend added a flight, departed, landed | Free | Friends |

Travel-day notification: from 3 h before departure until gate arrival, one ongoing
notification with progress, gate, and status. On Android 16+ it uses Live Updates
(`ProgressStyle`, promoted ongoing) so it shows as a status bar chip and on the lock
screen. Below 16 it is a plain ongoing notification with a progress bar. Updated by data
pushes, not by local timers.

Dedupe: an `alerts` row per (user, flight, type, value); the same gate change never sends twice.

---

## 5. Intelligence, v1 heuristics

Plain rules, each in one pure file with one test. Replace with learned models in v2
once we have six months of our own polled history.

### Delay prediction (`predict.ts`)

```
turnaround = { regional: 25, narrowbody: 35, widebody: 60 }  // minutes, by aircraft class
predictedDep = max(scheduledDep, inboundEstimatedGateArrival + turnaround[class])
if predictedDep - scheduledDep >= 15 min and airline estimate < predictedDep:
    alert "Likely delayed ~N min: your aircraft is running late from LAX"
```

Shown as "Likely", one number, no fake percentages.
ponytail: static turnaround table, upgrade to per-airline-per-airport medians from our own history.

### Connection (`connection.ts`)

```
layover         = next.scheduledDep - this.estimatedGateArrival
passportControl = zone(arrivalCountry) != zone(originCountry)   // zones: Schengen, UK+IE, US, CA, other=country
bagRecheck      = international arrival into US or CA with an onward leg
terminalChange  = arrTerminal != depTerminal (when both known)
minimum         = (domestic ? 45 : 90) + (terminalChange ? 30 : 0) + (bagRecheck ? 30 : 0)
risk            = layover < minimum ? Tight : layover < minimum + 30 ? Watch : Fine
```

ponytail: zone table and minimums are static, replace with per-airport MCT data when licensed.

### Route performance

Last 60 days of this ident from the provider's history endpoint, cached per ident per day
on the server. Buckets: early, on time (within 15), late, 45+ late, cancelled.

### Airport delay trend

Mean of (estimated - scheduled) departure over flights we already poll at that airport in
the last 6 h. Needs at least 5 flights, otherwise hidden.

---

## 6. Data

### Providers

| Need | Source | Cost | Why |
|---|---|---|---|
| Status, schedule, gates, belt, inbound aircraft, history | FlightAware AeroAPI v4 | per call | only mainstream provider that exposes the inbound aircraft link and gates |
| Positions fallback | OpenSky Network | free, rate-limited | ADS-B when AeroAPI position is stale |
| US airport advisories | FAA NAS Status API | free | ground stops, GDPs |
| Weather | aviationweather.gov METAR/TAF | free | pilot-grade, decodes to one line |
| Airports with timezones | mwgg/Airports JSON, filtered to IATA | bundled | has tz, OurAirports does not |
| Airlines | OpenFlights airlines.dat plus a small JSON of check-in URLs for the top 50 | bundled | |
| Aircraft class | 40-line static map ICAO type to regional / narrowbody / widebody | bundled | |

AeroAPI is the main running cost and the reason Pro exists. Alternative: AeroDataBox,
cheaper, no inbound-aircraft link. Decision for the owner, spec assumes AeroAPI.

### Model (Postgres on Supabase)

```
users        id, name, photo_url, home_airport, nationality, is_pro, trial_flight_key, push_tokens[], alert_prefs jsonb
flights      key (UA123_2026-10-14_SFO), ident, date, origin, destination,
             sched_dep, est_dep, act_dep, sched_arr, est_arr, act_arr,  -- all timestamptz, gate times
             act_takeoff, act_landing, dep_terminal, dep_gate, arr_terminal, arr_gate, belt,
             status enum(scheduled, delayed, boarding, departed, in_air, landed, arrived, cancelled, diverted, unknown),
             aircraft_type, registration, aircraft_age, inbound_key, distance_km,
             position jsonb {lat, lon, alt, speed, heading, at}, provider_payload jsonb,
             next_poll_at, last_polled_at, watcher_count
trips        user_id, flight_key, confirmation, seat, fare_class, shared bool, added_at
friendships  user_a, user_b, status enum(pending, accepted), muted_by[] , created_at
invites      code, user_id, expires_at
alerts       id, user_id, flight_key, type, value, sent_at
route_stats  ident, computed_on, buckets jsonb
```

One canonical `flights` row per real flight: 500 users on the same flight cost one poll.
That is the whole cost model.

Times are stored in UTC and displayed in the airport's local zone with `Intl.DateTimeFormat`
(Hermes ships Intl on Android). Zone abbreviation shown only when origin and destination
zones differ. No date library.

### Server (Supabase Edge Functions, TypeScript)

| Function | Trigger | Does |
|---|---|---|
| `poll-flights` | pg_cron every minute | selects flights with `next_poll_at <= now()`, fetches provider, diffs, writes, inserts alerts, sends Expo push inline, sets `next_poll_at` |
| `search-flights` | app call | proxies provider search so the key never ships in the app |
| `positions` | app call | position for one flight key, 60 s cache |
| `route-stats` | app call | 60-day buckets, 24 h cache |
| `invite` | app call | creates or redeems an invite code |

Poll cadence:

| Time to departure | Poll every |
|---|---|
| more than 7 d | 24 h |
| 7 d to 48 h | 6 h |
| 48 h to 6 h | 30 min |
| 6 h to gate arrival | 3 min |
| in air, positions | 60 s, only while a client is looking |
| 2 h after gate arrival | stop |

### Offline

TanStack Query persisted to MMKV. Every screen renders from cache first. A hairline note
"Updated 14 min ago" appears when data is older than 5 min. Writes (notes, add, remove)
queue and retry.

---

## 7. Design system

One file, `src/ui/tokens.ts`. Nothing hard-codes a color, size or radius outside it.

### Color

Starting values, tune in the first build and verify AA for every pair before shipping.

| Token | Light | Dark | Use |
|---|---|---|---|
| surface | #F7F7F8 | #0E0F11 | screen background |
| surfaceRaised | #FCFCFD | #16181B | cards, sheets |
| hairline | #E4E5E8 | #26292E | dividers, card borders |
| text | #141517 | #ECEDEF | |
| textMuted | #6B7079 | #8E939C | labels, secondary |
| accent | #2457F5 | #5B82FF | see list below |
| onAccent | #FBFCFF | #FBFCFF | |
| good | #1E9E5A | #3DBF77 | early, on time, landed |
| warn | #D98B10 | #F0A62C | delayed, watch |
| bad | #D6383A | #F0595B | cancelled, diverted, tight |

Accent appears in exactly five places: primary button, selected tab icon, your own plane,
the Live pill, links. Nowhere else. Status colors appear only on StatusPill, timeline
deltas, connection risk, and the route performance bar. No gradients, no glow, no pure
black or white, no Material You dynamic color (the status semantics depend on a fixed palette).

### Type

Geist for UI, Geist Mono for every number that is data: times, flight numbers, gates, belts,
registrations, distances. `fontVariant: ['tabular-nums']` on all of them. Fonts load
before the splash is released; Roboto must never flash.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| display | 44 / 48 | 600 | airport codes, status sentence, logbook totals |
| headline | 28 / 32 | 600 | screen titles |
| title | 20 / 26 | 600 | section titles, card ident |
| body | 16 / 24 | 400 | |
| label | 13 / 18 | 500 | data labels, chips |

Uppercase tracked labels are allowed only as data labels above a value group inside a detail
screen (GATE, BELT, INBOUND), never above screen or section titles. Supports font scale up
to 1.3: RouteLine stacks codes above times when the width runs out.

### Shape

Two radii. Cards and sheets: 20. Interactive controls (buttons, chips, inputs, pills): full pill.
No other radius exists.

### Elevation

None by default. Hairlines and spacing group content. Cards are `surfaceRaised` with a
hairline border and no shadow. The only shadow is under the bottom sheet, tinted to the
surface hue.

### Components

Ten, all in `src/ui/`. Everything else is composition.

FlightCard, StatusPill, RouteLine, Timeline, DataRow, Button (primary, secondary, text),
Chip, Sheet, Skeleton, EmptyState.

Icons: `phosphor-react-native`, regular weight, one family, no hand-drawn SVG.

### Motion

Reanimated. Every animation has a reason, and all of them stop under reduce motion
(`AccessibilityInfo.isReduceMotionEnabled`).

| Motion | Reason |
|---|---|
| plane on RouteLine eases between 60 s position updates over 1 s | shows live-ness |
| StatusPill color crossfade on status change | state transition |
| skeleton shimmer | loading feedback |
| default Android screen transitions, predictive back | platform |
| native pull-to-refresh | platform |

No parallax, no springs on cards, no infinite loops other than the shimmer.

### Dark mode

Follows system, override in Settings. Map uses a dark style JSON. Status colors are lifted
in dark for contrast. Every screen is checked in both modes before a PR merges.

### Copy

- No em-dashes anywhere, including notifications. Hyphen only.
- One middle dot per line at most ("Gate B12 · Terminal 2").
- Deltas in words: "12 min late", never "+12".
- Local airport time, system 12/24 h preference.
- Notification title: flight and change. Body: reason and new value.
  "UA 123 delayed 25 min" / "Inbound aircraft is late. New departure 09:05."
- No exclamation marks, no "Oops", no emoji.

### States

Every screen defines all four: loading (skeleton matching the final layout), empty
(one sentence, one action), error (inline with Retry, cached data stays visible),
stale (the "Updated n min ago" note).

---

## 8. Stack

Ladder applied: platform feature first, then an installed dependency, then code.

| Need | Choice | Why not something else |
|---|---|---|
| App | Expo SDK (latest stable), TypeScript, Expo Router | file routing ships with Expo |
| Server state | @tanstack/react-query + MMKV persister | gives offline reads for free |
| Client state | MMKV hooks for settings, nothing else | under 5 keys, no store library |
| Backend | Supabase: Postgres, Auth (Google sign-in), Edge Functions, pg_cron | one vendor, SQL fits the model; Firebase is the alternative |
| Push | expo-notifications + Expo Push API from the edge function | one HTTP call, channels handled |
| Live Update | one Expo Module in Kotlin wrapping `NotificationCompat.ProgressStyle`, three methods: start, update, end | expo-notifications does not expose ProgressStyle; verify at build time, write the module only if still true |
| Maps | react-native-maps, Google provider, lite mode on Logbook | platform maps |
| Calendar | expo-calendar | |
| Date picker | @react-native-community/datetimepicker | native M3 picker |
| Billing | RevenueCat (react-native-purchases) | replaces receipt validation and entitlement code |
| Fonts | expo-font, Geist + Geist Mono (OFL) | |
| Icons | phosphor-react-native | |
| Animation | react-native-reanimated (bundled) | |
| Dates | `Intl` | no moment, no dayjs |
| Great circle | `geo.ts`, 15 lines | no geo library |
| Tests | Jest (expo preset), Maestro | |

Android: minSdk 26, targetSdk latest, 16 KB page size compliant, themed monochrome icon,
App Links for invites, `POST_NOTIFICATIONS` requested at the moment the first flight is
added with a one-line reason, no exact alarms, no background work, Doze-safe by design.

---

## 9. Project layout

```
app/
  _layout.tsx            providers, theme, fonts, auth gate
  (tabs)/
    _layout.tsx          navigation bar
    index.tsx            Flights
    today.tsx
    friends.tsx
    logbook.tsx
  flight/[key].tsx
  airport/[iata].tsx
  add.tsx
  settings.tsx
  paywall.tsx
src/
  api/                   supabase client, one query file per domain
  domain/                pure functions, one test each:
                         predict.ts connection.ts geo.ts logbook.ts calendarParse.ts status.ts
  ui/                    tokens.ts and the ten components
  native/live-update/    Kotlin module and TS binding
  data/                  airports.json airlines.json aircraftClass.json checkin.json
supabase/
  migrations/
  functions/             poll-flights search-flights positions route-stats invite
                         diff.ts (pure, tested) shared by poll-flights
```

---

## 10. Business

| | Free | Pro |
|---|---|---|
| Tracking, map, friends, Today | yes | yes |
| Alerts | 12 core types | all 20 |
| Calendar import | yes | yes |
| Logbook | 12 months | unlimited |
| Inbound aircraft, early delay warning | | yes |
| Connection | | yes |
| Route performance, aircraft details, airport trend | | yes |
| Travel-day Live Update | | yes |

Pro: monthly and yearly (prices are the owner's call, placeholders $4.99 / $49.99).
Every new account's first tracked flight has Pro unlocked (`trial_flight_key`).
Costs scale with distinct flights polled, not with users, because of the canonical flight row.

---

## 11. Testing

- Jest: one test per file in `src/domain/` and for `supabase/functions/diff.ts`. Smallest
  input that fails if the logic breaks. No fixtures, no mocks of the provider.
- Maestro, three flows: add a flight, open its detail, accept an invite link.
- Manual before release: both themes, font scale 1.0 and 1.3, reduce motion on, airplane
  mode on the detail screen, Android 16 and Android 12 devices.

---

## 12. Pre-flight checklist

- [ ] zero em-dashes in UI strings and notification templates
- [ ] accent used only in the five listed places
- [ ] two radii only, no card inside a card
- [ ] every data number is mono with tabular figures
- [ ] every screen has loading, empty, error and stale states
- [ ] every token pair passes WCAG AA on both surfaces
- [ ] every screen checked in light and dark
- [ ] reduce motion stops the plane easing and the shimmer
- [ ] font scale 1.3 clips nothing
- [ ] four notification channels exist and match the Settings groups
- [ ] no Roboto flash before fonts load
- [ ] no middle-dot chains, decorative dots, glows, gradients, emoji
- [ ] no Flighty feature names in UI or store listing
- [ ] notifications permission asked in context, not at launch

---

## 13. Open decisions

1. Flight data provider: AeroAPI (assumed) or AeroDataBox (cheaper, no inbound link).
2. Backend: Supabase (assumed) or Firebase.
3. Pro prices.
4. App name "Skyage" and the four tab names (Flights, Today, Friends, Logbook).
5. Whether calendar import stays free (assumed yes).
