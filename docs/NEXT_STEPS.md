# Next steps

## Owner actions (blocking live testing)

1. **Enable Phone sign-in** (SMS vendor + Supabase Phone provider + anti-abuse limits), then build with
   `-Pyabah.phoneOtp=true` and sign in on a real phone. Until then, no live call can be exercised.
2. **Mapbox Downloads token** (`sk.` with `DOWNLOADS:READ`) on the build machine, to compile the native map. Restrict the
   runtime public token (`MAPBOX_FRONTEND_PUBLIC_TOKEN`) to the app. Confirm Mapbox licensing for ride-hailing.
3. Security items S1–S6 in PHASE0_AUDIT.md (e.g. `revoke execute on function public.transition_trip from anon`).
4. Decide the Play Store identity: this project uses `com.yabah.app` (same as Flutter, so it can replace it with the same
   signing key; debug builds are `com.yabah.app.debug`).

## Engineering, in small verified slices

| Next slice | Contents | Server contracts |
| --- | --- | --- |
| 3b — first live run | Sign in on a device; verify `cities`, `taxi-map-config`, one route preview; then **one** quote and **one** test booking only with explicit owner approval | already wired |
| 4b — passenger completion | Trip history (`trip_history`), saved places (RLS on `saved_places`), notifications inbox, profile/phone update, support tickets; Realtime as a refresh hint | read sources of `taxi-notifications` first |
| 5 — captain | Role/approval from `captain_status`; 4-step onboarding with documents from `required_document_types` (Storage RLS); online switch (`update_presence`) with a visible foreground location service; `nearby_requests`; accept/arrive/start/complete; cash confirm/dispute; earnings/subscriptions read-only | read `my_captain_status` body and storage policies first |
| 6 — hardening | FCM push (needs Firebase + `taxi-push` review), lifecycle/process-death tests on devices, baseline profiles, accessibility audit with TalkBack, release signing, Play listing | — |
