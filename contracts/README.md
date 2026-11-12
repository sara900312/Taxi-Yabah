# YABAH contracts (platform-neutral)

JSON Schemas (draft 2020-12) for the passenger slice, written from the **deployed** edge-function source and database
functions read on 12 Nov 2026 (see `../docs/PHASE0_AUDIT.md`). They are meant to be shared by the Android (Kotlin)
client and a future iOS/Flutter client. **The server is authoritative**; re-check these after any function redeploy.

| File | Function / action |
| --- | --- |
| `error.schema.json` | Error envelope used by every function |
| `taxi-map-config.public_map_token.json` | Public Mapbox token for rendering |
| `taxi-passenger-route.json` | Road route preview |
| `taxi-pricing.quote_fare.json` | Standard taxi quote |
| `taxi-trips.request_trip.json` | Booking |
| `taxi-trips.trip_details.json` | `trip_details` / `active_trip` |
| `taxi-trips.transition_and_rate.json` | `transition_trip`, `rate_trip` |

## Semantics every client must keep

1. **Auth:** `Authorization: Bearer <user access token>` and `apikey: <publishable key>`. Never ship a service-role or
   `sb_secret_` key, or a Mapbox `sk.` token. `INVALID_SESSION` is decided before any write, so one retry after a
   token refresh is safe.
2. **Service area:** governorate outline **and** radius around the city centre. Clients may pre-check with the same
   outline file, but only the server's answer counts.
3. **Route:** draw only a server `LineString`. No straight-line or local "estimated road" fallback.
4. **Quote:** request only on an explicit user action (each one is billable and inserts a row). Show the server amount
   (`options[].final_fare_iqd` for the offered class) and expiry, converted with the HTTP `Date` header. Changing either
   point invalidates the quote.
5. **Booking:** `request_trip` is not idempotent. Disable the button while submitting. On a timeout or lost answer,
   call `active_trip` first; if that is also unreachable, keep booking disabled and offer "check again". Never resend
   automatically.
6. **Trip state:** `requested → accepted → arrived → in_progress → completed`, or `cancelled` from
   requested/accepted/arrived. Re-read `active_trip` on app start/resume; poll `trip_details` while visible (Realtime may
   be added as a refresh hint, never as the source of truth). Captain location is stale when the server-clock age is
   ≥ 2 minutes.
7. **Payment:** cash only for now (wallet exists server-side but is not offered).
8. **Errors:** display a mapped human message per `error` code; never display `message`.
