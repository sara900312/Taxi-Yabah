# Native Mapbox map

Two different Mapbox tokens are involved:

| Token | Where | Purpose |
| --- | --- | --- |
| **Public** `pk.…` | Fetched at runtime from `taxi-map-config` (`public_map_token`) with the user's session | Map rendering only. The app refuses anything that is not `pk.<x>.<y>`. Never stored in the repo or APK. |
| **Secret Downloads** `sk.…` with scope `DOWNLOADS:READ` | **Build machine only**: `~/.gradle/gradle.properties` → `MAPBOX_DOWNLOADS_TOKEN=sk.…` (or env var) | Lets Gradle download the Mapbox SDK from Mapbox's private Maven. Never committed, never packaged. |

## Status

- Default build (no Downloads token): `src/demomap/` is compiled. `NativeMap.isAvailable=false` and the app uses the
  **simplified local map** (governorate outline, service radius, GeoNames places), always badged «خريطة مبسطة · مو خريطة شوارع».
  The demo canvas really pans, pinch-zooms, taps, fits bounds and draws server routes, but it has no streets.
- Native build (Downloads token present): `src/mapbox/kotlin/.../NativeMap.kt` + `com.mapbox.maps:android-ndk27` and
  `com.mapbox.extension:maps-compose-ndk27` (`yabah.mapboxVersion`, default `11.18.0`). The surface uses `MapboxMap`,
  `MapViewportState.easeTo/cameraForCoordinates` with the bottom-sheet padding, `subscribeMapIdle` for pin selection,
  `PolylineAnnotation` for the server route and `CircleAnnotation` for markers.
  **UNVERIFIED: this source set has not been compiled or run** (no Downloads token in the build session).
- The native surface is used only in LIVE mode with a server-issued public token, so the demo never needs Mapbox.

## Verify on a device (after adding the Downloads token)

```bash
./gradlew :app:assembleDebug -PMAPBOX_DOWNLOADS_TOKEN=sk.your_downloads_token
```

1. Fix any API drift in `NativeMap.kt` against the chosen SDK version (the Compose annotation API has changed between 11.x minors).
2. Sign in (needs the Phone provider). Confirm the map shows Mapbox attribution and the logo, that panning moves the
   centre pin, that tapping moves the camera, and that the route fits above the sheet in all three sheet states.
3. Test on a budget phone (2–3 GB RAM): first render time, memory, and frame pacing while dragging the sheet.
4. Owner/legal: confirm Mapbox terms for ride-hailing and MAU pricing, and restrict `MAPBOX_FRONTEND_PUBLIC_TOKEN` to the app
   (Flutter BACKEND_BLOCKERS B9).

Google AI Studio's in-browser emulator is **not** evidence that a native map renderer works. Verify on a real device.
