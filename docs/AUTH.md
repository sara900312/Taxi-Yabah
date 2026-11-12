# Authentication — phone first

**Decision followed:** the owner removed Google Sign-In on 11 Nov 2026 (recorded in the Flutter repo's
`docs/PHONE_AUTH.md`). This native app therefore has **no Google/OAuth code, no client IDs and no redirect deep link**.
People sign in with their Iraqi mobile number and a one-time SMS code (Supabase Phone Auth).

## Build switch

| Build | Sign-in screen | Server contact |
| --- | --- | --- |
| Default (`yabah.phoneOtp=false` in `gradle.properties`) | Number field and button disabled; «تسجيل الدخول برقم الموبايل ورمز التحقق راح يتفعّل قريباً»; "Try the demo preview" | **None** (unit test `defaultBuildNeverSendsAnSms`) |
| `./gradlew assembleDebug -Pyabah.phoneOtp=true` | Phone → 6-digit code (60 s resend lock) → name (first time) → home | `POST /auth/v1/otp`, `POST /auth/v1/verify`, `GET/PATCH /rest/v1/profiles` (own row, RLS) |

Do **not** ship the OTP build until the owner has: chosen an SMS vendor that delivers to Iraq, enabled the Phone provider
with OTP length 6 / expiry ≤ 5 min / rate limits and CAPTCHA against SMS pumping, and linked the two legacy Google-created
accounts (see the Flutter repo's PHONE_AUTH.md §3). Recommended server gate: require `profiles.phone_verified_at` for
`request_trip`, captain applications and going online.

## Implementation

- `PhoneNumbers` accepts `07XXXXXXXXX`, `+9647XXXXXXXXX`, `009647…`, `9647…`, Arabic-Indic digits; same rule as Flutter
  (`^(07\d{9}|\+9647\d{9})$`). Invalid numbers are never sent.
- `AuthRepository` (GoTrue over REST): `sendOtp`, `verifyOtp`, single-flight refresh (`grant_type=refresh_token`),
  sign-out. A 4xx on refresh clears the session (→ sign-in screen); offline keeps it.
- GoTrue errors map to `OtpInvalidOrExpired`, `OtpRateLimited`, `SignInUnavailable` (provider disabled),
  `AccountConflict` (`PHONE_ALREADY_REGISTERED` from the profile trigger), `InvalidPhone`, `Offline`. Raw GoTrue text is
  never shown.
- **Session storage:** AES-256-GCM, key generated in the **Android Keystore** (non-exportable); ciphertext in private
  SharedPreferences. If the Keystore is unusable the session lives in memory only. Tokens are never written in plain text,
  never logged (`Session.toString()` hides them), and excluded from backup/device transfer.
- Suspended accounts: after verification the profile's `account_status` is read; non-`active` → blocked screen. Every
  passenger function also returns `ACCOUNT_NOT_ACTIVE`, shown as «حسابك مو مفعّل أو موقوف».
- Unverified phone numbers: the app relies on Supabase verification (the session exists only after a correct code).

## Not verified

A real SMS sign-in. Per the Flutter audit (11 Nov) every Supabase auth provider is disabled. The connector used today
cannot read auth settings, so this was **not re-verified on 12 Nov**. Until a provider is enabled, nobody can get a session,
and live mode (routes, quotes, bookings) cannot be exercised end-to-end.
