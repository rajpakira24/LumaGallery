# Server-Side AI Keys — Supabase Proxy + Play Integrity

**Date:** 2026-06-20
**Status:** Approved (design)

## Problem

AI provider keys (`GEMINI_API_KEY`, `OPENROUTER_API_KEY`) are compiled into
`BuildConfig` and ship inside the APK. They are trivially extractable with
`apktool`/`jadx`. Anyone can pull them and drain the project's Gemini /
OpenRouter quota and billing. They must move server-side.

On-device background removal (`OnDeviceBgRemover`, ML Kit Selfie Segmentation)
uses **no key** and stays fully local — out of scope for this change.

## Goal

- No AI provider key present anywhere in the APK.
- App calls a Supabase Edge Function ("the proxy") which holds the keys and
  forwards to Gemini / OpenRouter.
- The proxy is gated by **Google Play Integrity** attestation so only the real,
  unmodified app can use it.

## Non-Goals

- User accounts / login (app remains login-free).
- Changing on-device background removal.
- Moving the Unity Ads game ID server-side (it is public by design).

## Architecture

```
Android app                         Supabase Edge Function          Providers
-----------                         ----------------------          ---------
EditViewModel
  └ AiEditRepository
      └ AiProxyClient ── HTTPS ──▶  ai-proxy (Deno/TS)
          ▲                          1. verify Play Integrity token
   IntegrityTokenProvider            2. route by op
   (Play Integrity Standard)         3. attach secret key ─────────▶ Gemini / OpenRouter
                                      4. return result
OnDeviceBgRemover (ML Kit) ── stays 100% local, never hits proxy
```

## Components

### 1. `ai-proxy` Edge Function (Deno / TypeScript)
- Single HTTPS entry point. Switches on request field `op`:
  `upscale | inpaint | prompt_edit | generate | describe`.
- **Step 1 — attestation:** verify the Play Integrity token via Google
  `playintegrity.googleapis.com/v1/{packageName}:decodeIntegrityToken` using a
  service account. Reject unless:
  - `appRecognitionVerdict == PLAY_RECOGNIZED`
  - `packageName == com.webstudio.lumagallery`
  - `requestHash` matches the hash the client bound to this payload
- **Step 2 — route:** `describe` → OpenRouter chat/completions; all others →
  Gemini `generateContent` (`gemini-2.5-flash-image`).
- **Step 3 — secrets:** read `GEMINI_API_KEY` / `OPENROUTER_API_KEY` from
  Supabase secrets; never returned to client.
- **Error mapping:** provider `429` → `{error:"quota"}` (HTTP 429); attestation
  fail → HTTP 401; other provider failure → HTTP 502 `{error:"failure"}`.
- Gemini response parsed with correct **camelCase** keys (`inlineData`,
  `mimeType`) — fixes the latent snake_case parse bug that lived in the old
  client.

### 2. `IntegrityTokenProvider` (Android, `data/ai/`)
- Wraps the Play Integrity **Standard** API (`prepareIntegrityToken` warmup,
  then `request(requestHash)`).
- Exposed behind an interface (`IntegrityTokenProvider`) so consumers are
  testable with a fake. Real impl: `PlayIntegrityTokenProvider`.
- `suspend fun token(requestHash: String): String`.

### 3. `AiProxyClient` (Android, `data/ai/`)
- Constructor: `functionUrl`, `anonKey`, `IntegrityTokenProvider`, OkHttp client.
- `editImage(op, src, mask?, prompt?): Bitmap` and `describe(src): String`.
- Flow: compute `requestHash` = sha256(op + image bytes) → get integrity token →
  POST `{op, image_b64, mask_b64?, prompt?, integrity_token}` with headers
  `apikey: <anon>` and `Authorization: Bearer <anon>` → parse `{image_b64}` /
  `{text}`.
- Maps HTTP 401 → `AttestationException`, 429 → `QuotaException`,
  `UnknownHostException` → network, else `IOException`.

### 4. `AiEditRepository` refactor
- Delegates all cloud ops to `AiProxyClient`. **Delete** `GeminiImageClient` and
  `OpenRouterVisionClient` — their key-holding logic is obsolete.
- Keep `AiResult`, `OnDeviceBgRemover`, caption caching (`caption_$photoId`).
- Replace `hasGeminiKey` / `hasOpenRouterKey` with a single
  `aiEnabled: Boolean = SUPABASE_FUNCTION_URL.isNotBlank()`.
- Error→`AiResult` mapping: `AttestationException` → `AiResult.Failure("device verification failed")`,
  `QuotaException` → `QuotaExceeded`, network → `NetworkError`.

## Data Flow (image edit example)

1. `EditViewModel` → `AiEditRepository.upscale(bmp)`
2. `AiProxyClient.editImage("upscale", bmp)`:
   - `requestHash = sha256("upscale" + jpegBytes)`
   - `token = integrity.token(requestHash)`
   - POST to `{SUPABASE_FUNCTION_URL}/ai-proxy`
3. Function verifies token, calls Gemini, returns `{image_b64}`
4. Client decodes → `AiResult.Success(bitmap)`

`describe` is identical with `op="describe"`, returns `{text}` →
`AiResult.TextSuccess`.

## Configuration Changes

**Remove** from `local.properties` → `BuildConfig`:
`GEMINI_API_KEY`, `OPENROUTER_API_KEY`.

**Add** to `local.properties` → `BuildConfig`:
`SUPABASE_FUNCTION_URL`, `SUPABASE_ANON_KEY` (anon key is publishable — safe in client).

**Supabase secrets** (`supabase secrets set`):
`GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `PLAY_INTEGRITY_SA` (service-account JSON),
`ANDROID_PACKAGE_NAME=com.webstudio.lumagallery`.

**Gradle** (`app/build.gradle.kts`): add `com.google.android.play:integrity:1.4.0`.

**Docs:** update `CLAUDE.md` AI-layer + Key-Constraints sections; update README.

## Testing

- **Edge function (Deno):** token-verify pass/fail, op routing, provider error
  mapping (mock `fetch`), requestHash mismatch rejection.
- **Android:** `AiProxyClient` request-building + response/error parsing via
  MockWebServer; consumers tested against a fake `IntegrityTokenProvider`.
- On-device path unchanged; existing behavior preserved.

## Hard Prerequisites (block production)

1. **Google Play Console** — app registered + linked to a GCP project for Play
   Integrity verdicts. Dev testing via license testers / internal app sharing.
2. **GCP service account** with Play Integrity API enabled → JSON → Supabase secret.
3. **Rotate `OPENROUTER_API_KEY`** — the current real key sat in plaintext
   `local.properties`; treat as compromised and regenerate.
4. **Supabase project** — free org caps at 2 active projects (FitDost, FreeTube
   already live); create a new project for LumaGallery or reuse one.

## Rollout Order

1. Create/select Supabase project; set secrets.
2. Deploy `ai-proxy` (attestation can be stubbed to allow-all behind a debug flag
   for first end-to-end test, then enforced).
3. Android: add Integrity dep, `IntegrityTokenProvider`, `AiProxyClient`, refactor
   repository, swap capability flag, update BuildConfig fields.
4. Remove old keys from `local.properties`; delete old clients.
5. Enforce attestation in the function; rotate OpenRouter key.
