# Test evidence — 12 Nov 2026

**Environment:** cloud Linux container, JDK 21, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21, compileSdk/targetSdk 36,
minSdk 24. **No hardware emulator** (no KVM), so UI tests run on **Robolectric 4.16 (Android 15 framework, native graphics)**:
real Compose layout, real touch dispatch, real rendering. Screenshots are captured with Roborazzi from that rendering.

```bash
./gradlew testDebugUnitTest          # all JVM + Robolectric tests
./gradlew recordRoborazziDebug       # same, and (re)writes docs/screenshots/*.png
./gradlew assembleDebug assembleRelease
```

## Results

| Suite | Tests | Result | What it proves |
| --- | --- | --- | --- |
| `CoreRulesTest` | 7 | ✅ | Iraqi number normalisation, server-identical service-area rule (outline AND radius), haversine, Mercator, Arabic search folding, every verified server code has a human message, cancel rule |
| `ContractTest` (MockWebServer) | 12 | ✅ | Exact request bodies/headers for route, quote, request_trip; response parsing; invalid geometry refused; error codes kept; quote expiry via server `Date`; lost answer = NoAnswer and **not re-sent**; unreachable = Offline; INVALID_SESSION → one refresh → retry; dead refresh token signs out; trip_details shape; only `pk.` map tokens; GoTrue OTP mapping |
| `PassengerHomeViewModelTest` | 16 | ✅ | Demo makes **zero** server calls and cannot book; route requested once, only after both points; editing invalidates route and quote; late answers ignored; server errors shown; quote expiry blocks booking; **double tap submits once**; timeout reconciled with `active_trip`; unresolved stays blocked until checked; existing active trip opens; offline never sends |
| `TripAndSignInViewModelTest` | 8 | ✅ | Polls while visible, stops when terminal or hidden; stale captain location by server clock; cancel only when allowed and once; rating once; ALREADY_RATED → done; default build never sends an SMS; OTP flow phone → code → name |
| `SheetStateTest` | 2 | ✅ | Drag clamping, position/velocity settling |
| `AppUiTest` (Robolectric UI) | 10 | ✅ | See below |
| **Total** | **55** | **55 passed, 0 failed** | |

Builds: `assembleDebug` ✅ (13.9 MB), `assembleRelease` with R8 ✅ (2.2 MB unsigned). Kotlin warnings: 0.
APK scan: no `sb_secret_`, `service_role`, `sk.eyJ`, private key; only the publishable key.

## UI tests (real gestures)

| Test | Gesture / assertion |
| --- | --- |
| `demoHome_sheetReallyDragsBetweenThreeStates` | Fling up on the grabber → **Expanded**; fling down → **Collapsed**; tap grabber → **Half**; slow drag → settles by position |
| `demoFlow_tapAndPanToChoosePoints_neverShowsAFareOrBooks` | Tap map → pickup label «قرب …»; confirm; **drag-pan** map → destination; confirm → demo notice, no fare node, no price button, booking disabled; repository calls = 0 |
| `offlineSearch_choosingAPlaceMovesThePin` | Type «منصور» → first result «المنصور» → tap → pin label «قرب المنصور» |
| `signIn_defaultBuildSendsNothingAndOffersDemo` | Coming-soon message, send disabled, demo entry works |
| `onboarding_ar` | Three pages → sign-in |
| `english_isLeftToRight`, `darkTheme` | Locale and theme rendering |
| `fixture_liveFlowRendersServerAnswersAndNavigatesToTrip` | *Fake server.* Route → booking disabled until an explicit price → price shown → request → trip screen; exactly 1 route, 1 quote, 1 request |
| `fixture_tripAcceptedWithStaleCaptainLocation` | Captain card, stale warning, cancel offered |
| `fixture_completedTripRatesOnce` | No cancel; 5 stars → submit once |

## Screenshots (`docs/screenshots/`)

Files `01–10` are the **DEMO** build exactly as a user sees it. Files named **`fixture_…` render invented test data through a
fake repository** to show how live server answers are presented. **They are not evidence of a working backend call.**

| | |
| --- | --- |
| 01 home, sheet half (ar) | 02 sheet expanded | 
| 03 sheet collapsed | 04 choosing destination |
| 05 review (demo: no price) | 06 offline search |
| 07 sign-in (OTP off) | 08 onboarding |
| 09 English LTR | 10 dark theme |
| 11 fixture: server route + quote | 12 fixture: searching for captain |
| 13 fixture: accepted, stale location | 14 fixture: completed + rating |

## Not verified (honest list)

- **Any live backend call from the app.** No session can be obtained (auth providers reportedly disabled). No route, quote
  or trip was requested against production, by design.
- Native Mapbox rendering (source set not compiled: needs the Downloads token). See MAPBOX.md.
- Real device behaviour: GPS fixes, permission dialogs, keyboard/IME on physical keyboards, low-end performance, process death.
- Release APK at runtime (R8 build succeeds; not launched).
- Google AI Studio import of this project (not available from this environment).
