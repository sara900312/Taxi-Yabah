# Phase 0 — Read-only audit (12 Nov 2026)

Everything below was read on **12 Nov 2026** through the Supabase management connector (read-only SQL `SELECT`s,
function source downloads, advisors). **No write of any kind was made**: no SQL DDL/DML, no RLS/storage/auth change,
no function deploy, no trip, quote, user or record created. The Flutter repository `sara900312/Taxi` was cloned
read-only as a reference (nothing committed there). `sara900312/cityride-taxi` was **not** accessed.

## 1. Backend inventory (verified)

| Item | Value |
| --- | --- |
| Project | `Taxi`, ref `ifawmzjggjrgboufrxzk`, region `eu-west-2`, Postgres 17, status `ACTIVE_HEALTHY` |
| Tables | 34 in `public`, 4 in `private` (matches the brief) |
| RLS disabled | only `private.push_dispatch_nonces`, `private.schema_baselines` (all 34 public tables have RLS on) |
| Edge functions (9, all ACTIVE) | `taxi-trips` v5, `taxi-notifications` v3, `taxi-pricing` v6, `taxi-pricing-admin` v2, `taxi-wallet` v3, `taxi-admin` v4, `taxi-push` v3 (**verify_jwt=false**), `taxi-map-config` v1, `taxi-passenger-route` v2 |
| Client keys | publishable `sb_publishable_…` (used by the app) and the legacy anon JWT. No secret key is in the app (APK scan: 0 hits for `sb_secret_`, `service_role`, `sk.eyJ`). |

### Live configuration that the app must NOT hard-code

| Config | Live value (12 Nov) | App behaviour |
| --- | --- | --- |
| `cities` | Only **Baghdad (id 1)** has `is_active=true, rides_enabled=true` (centre 33.3152, 44.3661; radius 30 000 m). Basra, Najaf, Karbala, Hilla, Diyala, Mosul, Kirkuk, Nasiriyah: inactive, rides disabled. | Live mode reads `/rest/v1/cities?is_active=eq.true&rides_enabled=eq.true`. Demo mode uses a labelled snapshot of Baghdad only. |
| `vehicle_classes` | economy أوفر (×0.9, min 2 000), taxi تكسي (×1.0, min 2 500), comfort مريح (×1.35, 4 000), family عائلي (×1.6, 5 000, 7 seats), tuktuk تكتك (×0.6, 1 000, 2 seats) — all active | Live mode shows **only** the class the server quotes (`options[]`, today only `taxi`). Demo lists the catalogue with "price comes from the server". |
| `taxi_pricing_settings` | **`is_enabled = true`**, `quote_ttl_seconds = 180`, `settings_version = 2`, base/per-km set | Pricing is currently ON. The app still handles `DYNAMIC_PRICING_NOT_CONFIGURED` (503) for when it is switched off. |
| Auth providers | **Not readable through the connector.** The Flutter repo's `docs/PHONE_AUTH.md` records (11 Nov) `google:false, phone:false, email:false, anonymous:false` | **Unverified today.** If still true, nobody can obtain a session, so live mode cannot be exercised end-to-end. |

## 2. Contract matrix (from deployed source)

All functions: `POST`, `Authorization: Bearer <user JWT>`, `apikey: <publishable>`; error envelope
`{ "ok": false, "error": "CODE", "message"? }`. Machine-readable schemas: [`../contracts/`](../contracts/).

| Function / action | Request | Success | Error codes (verified) | App |
| --- | --- | --- | --- | --- |
| `taxi-map-config` `public_map_token` | `{action}` | `{ok, token:"pk.…"}` | AUTH_REQUIRED, INVALID_SESSION, UNSUPPORTED_ACTION, MAPBOX_PUBLIC_TOKEN_UNAVAILABLE, MAP_BOOTSTRAP_UNAVAILABLE | Wired (native-map builds only); rejects anything not `pk.x.y` |
| `taxi-passenger-route` | `{pickup:{lat,lng}, dropoff:{lat,lng}, city_id?}` | `{ok, route:{geometry:LineString, distance_m, duration_s}}` | INVALID_ROUTE_DETAILS, ROUTE_TOO_SHORT (<5 m), ACCOUNT_NOT_ACTIVE, PASSENGER_ROLE_REQUIRED, OUTSIDE_SERVICE_AREA, DROPOFF_OUTSIDE_SERVICE_AREA, MAPBOX_NOT_CONFIGURED, MAPBOX_BUDGET_CHECK_UNAVAILABLE, QUOTE_RATE_LIMITED (429), MAPBOX_DAILY_LIMIT_REACHED, ROUTE_NOT_FOUND, MAPBOX_ROUTE_UNAVAILABLE | Wired + contract-tested. Invalid geometry is refused (never a straight-line fallback) |
| `taxi-pricing` `quote_fare` (+`offer:"standard_taxi"`, `city_id`) | addresses (3–500 chars) + 4 coordinates | `{ok, quote:{id, estimated_fare_iqd, currency, distance_m, duration_s, expires_at, city_id, options:[{vehicle_class:"taxi", final_fare_iqd, …}], pricing_snapshot}}` | ACCOUNT_NOT_ACTIVE, PASSENGER_ROLE_REQUIRED, INVALID_ROUTE_DETAILS, DYNAMIC_PRICING_NOT_CONFIGURED (503), OUTSIDE_SERVICE_AREA, DROPOFF_OUTSIDE_SERVICE_AREA, VEHICLE_CLASS_NOT_AVAILABLE, MAPBOX_ACCESS_TOKEN_NOT_CONFIGURED, QUOTE_RATE_LIMITED, MAPBOX_DAILY_LIMIT_REACHED, MAPBOX_* provider codes, 500 + message | Wired + tested. Expiry converted via the server `Date` header. **Inserts a `trip_fare_quotes` row**, so the app only quotes on an explicit tap |
| `taxi-trips` `request_trip` | `fare_quote_id` (uuid), `vehicle_class` (`taxi`), `payment_method` (`cash`), `passenger_count` 1–8, `passenger_note?`, `promo_code?` | 201 `{ok, trip:<trips row>}` | VALID_FARE_QUOTE_REQUIRED, INVALID_VEHICLE_CLASS, INVALID_PAYMENT_METHOD, INVALID_TRIP_DETAILS; RPC: FARE_QUOTE_EXPIRED, FARE_QUOTE_ALREADY_USED, FARE_QUOTE_NOT_FOUND, PRICE_CHANGED, DYNAMIC_PRICING_DISABLED, OUTSIDE_SERVICE_AREA, CROSS_CITY_NOT_SUPPORTED, VEHICLE_CLASS_NOT_IN_QUOTE, VEHICLE_CLASS_NOT_AVAILABLE, PAYMENT_METHOD_DISABLED, PROMOS_DISABLED, INVALID_PASSENGER_COUNT/NOTE, ACCOUNT_NOT_ACTIVE, PASSENGER_HAS_ACTIVE_TRIP | Wired + tested; never auto-retried |
| `taxi-trips` `active_trip` / `trip_details` | `{}` / `{trip_id}` | `{ok, details:null|{trip, vehicle_class, passenger, captain, vehicle, captain_location, events[], my_rating_submitted}}` | TRIP_NOT_FOUND, AUTH_REQUIRED | Wired + tested (reconciliation, polling) |
| `taxi-trips` `transition_trip` | `{trip_id, trip_action, reason?, final_fare_iqd?}` | `{ok, trip}` | INVALID_TRIP_ACTION, INVALID_FINAL_FARE; RPC: TRIP_CANNOT_BE_CANCELLED (passenger may cancel only in requested/accepted/arrived), NOT_TRIP_PARTICIPANT, INVALID_TRIP_TRANSITION, … | Passenger `cancel` wired; captain actions not yet |
| `taxi-trips` `rate_trip` | `{trip_id, stars 1–5, comment?, tags?}` | 201 `{ok, rating}` | INVALID_RATING; RPC: TRIP_NOT_COMPLETED, RATING_WINDOW_CLOSED, ALREADY_RATED, NOT_TRIP_PARTICIPANT | Wired + tested (one-time) |
| `taxi-trips` captain actions (`nearby_requests`, `update_presence`, `captain_status`, `submit_application`, `set_active_vehicle`, `retire_vehicle`, `confirm_cash`, `dispute_cash`), `trip_history`, `preview_promo`, `update_phone` | read from source | — | — | **Not wired yet** (Phase 4–5) |
| `taxi-notifications`, `taxi-push`, `taxi-wallet`, `taxi-admin`, `taxi-pricing-admin` | **source not read in this phase** | | | Not used by the passenger slice |

`get_trip_details` exposes the captain's phone only while `status ∈ {accepted, arrived, in_progress}` and the captain
location only in those states. The Mapbox routing budget (`mapbox_quote_budget_reserve`) is shared by route
previews and quotes.

## 3. Service-area rule (server, copied exactly to the client as a hint)

`inServiceArea(p) = inside(governorate outline ring for city.code) AND haversine(p, city centre) ≤ service_radius_m`.
The outline file is the server's own `regions/boundaries.json` (byte-identical to the Flutter asset; OCHA COD-AB,
CC BY 3.0 IGO). The radius is **not** the governorate boundary; both must hold. The client uses this only to warn
early. Routes, quotes and bookings are always decided by the server.

## 4. Security review (recommendations only, nothing applied)

| # | Finding | Severity | Recommendation |
| --- | --- | --- | --- |
| S1 | `private.push_dispatch_nonces`, `private.schema_baselines` have RLS disabled | Low **if** `private` is not exposed via PostgREST (default exposed schemas are public/graphql_public) | Confirm `private` is not in the API's exposed schemas and that `anon`/`authenticated` have no grants on these tables; enabling RLS with no policies is a cheap safety net |
| S2 | `public.transition_trip` is EXECUTE-able by `anon` (re-verified today) | Low (the private function raises AUTH_REQUIRED for null uid) | `revoke execute on function public.transition_trip from anon;` |
| S3 | `taxi-trips` returns raw DB `message` text | Info leak | Return code + correlation id only (the app never displays `message`) |
| S4 | Advisor WARN: `public.taxi_rider_service_status()` SECURITY DEFINER callable by `authenticated` | Review | Confirm intended (non-sensitive flags) |
| S5 | Advisor WARN: leaked-password protection off | Only matters if password sign-in is enabled | Enable if passwords are ever used |
| S6 | `taxi-push` deployed with `verify_jwt=false` | Depends on its own auth (not read) | Read its source and confirm it authenticates callers (e.g. webhook secret) before launch |
| S7 | Advisor INFO: `private.mapbox_route_*` RLS on, no policies | Intended (server-only) | none |

## 5. Android permissions (this build)

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` (passenger pickup, foreground,
on demand). **Not requested:** background location, foreground service, notifications. These belong to the captain
phase and need a visible foreground service and an explained privacy flow. Backups are disabled; the session is
excluded from cloud backup and device transfer.

## 6. Demo / live boundary

| | DEMO | LIVE |
| --- | --- | --- |
| Entry | "جرّب المعاينة التجريبية" on the sign-in screen | A real Supabase session (phone OTP) |
| Network | **none** (repository throws `DemoUnsupported`; UI test asserts zero repository calls) | Edge functions + PostgREST as the user, RLS applies |
| City | Labelled snapshot of Baghdad | `cities` from the server |
| Route | Dashed straight line labelled «خط مباشر للتوضيح فقط · مو مسار طريق» | Server LineString only |
| Price | Never shown. Cards say «السعر يجي من الخادم بعد تسجيل الدخول» | Server quote with expiry |
| Booking | Disabled button «الحجز مو متاح بالمعاينة التجريبية» | `request_trip` with single-submit and reconciliation |
| Marking | DEMO chip, "DEMO" map watermark, banner | Simplified-map badge while the native map is absent |

## 7. Differences from the master brief

1. **Sign-in:** the brief says "Google OAuth if configured". The owner removed Google sign-in on 11 Nov 2026
   (Flutter `docs/PHONE_AUTH.md`). This app follows that later decision: phone + SMS OTP, **off** until the
   owner enables the Phone provider. See [AUTH.md](AUTH.md).
2. **Pricing** is enabled today (the brief warned it may be disabled). Both paths are handled.
3. **Native Mapbox** needs a Mapbox *secret* Downloads token to compile (not available here). See [MAPBOX.md](MAPBOX.md).
