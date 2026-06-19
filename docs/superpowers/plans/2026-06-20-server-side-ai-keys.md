# Server-Side AI Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `GEMINI_API_KEY` and `OPENROUTER_API_KEY` out of the APK into a Supabase Edge Function gated by Google Play Integrity, so the app calls a proxy that holds the keys.

**Architecture:** Android `AiEditRepository` → `AiProxyClient` (attaches a Play Integrity token) → Supabase `ai-proxy` edge function (verifies token, attaches secret key, forwards to Gemini/OpenRouter). On-device background removal is untouched.

**Tech Stack:** Supabase Edge Functions (Deno/TypeScript), Google Play Integrity Standard API, Kotlin + OkHttp + kotlinx.serialization, ML Kit (unchanged).

**Spec:** `docs/superpowers/specs/2026-06-20-server-side-ai-keys-design.md`

> **Shell note:** Gradle cannot run in this shell (loopback error) — Android unit tests in Tasks 7–11 run from **Android Studio** (Run gutter) or a normal terminal, not via the agent's shell. Deno tests (Tasks 1–4) run fine in shell.

---

## File Structure

**Supabase (new dir `supabase/functions/ai-proxy/`):**
- `index.ts` — HTTP entry, request parse, op routing, error→HTTP mapping.
- `integrity.ts` — Play Integrity token verification (service-account OAuth + decode).
- `providers.ts` — Gemini + OpenRouter HTTP calls.
- `index_test.ts`, `integrity_test.ts`, `providers_test.ts` — Deno tests.
- `deno.json` — task/test config.

**Android (`app/src/main/java/com/webstudio/lumagallery/data/ai/`):**
- Create: `IntegrityTokenProvider.kt` (interface + `PlayIntegrityTokenProvider`).
- Create: `AiProxyClient.kt`.
- Modify: `AiEditRepository.kt` (delegate to proxy, `aiEnabled` flag).
- Delete: `GeminiImageClient.kt`, `OpenRouterVisionClient.kt`.
- Modify: `ui/viewmodel/EditViewModel.kt`, `ui/viewmodel/GalleryViewModel.kt`, `ui/screens/edit/panels/AiPanel.kt`, `ui/screens/edit/EditScreen.kt`, `ui/screens/GalleryScreen.kt` (`hasGeminiKey` → `aiEnabled`).
- Modify: `app/build.gradle.kts` (deps + BuildConfig fields), `local.properties`.
- Test: `app/src/test/java/com/webstudio/lumagallery/data/ai/AiProxyClientTest.kt`.

---

## Task 1: Scaffold edge function + op routing (attestation stubbed)

**Files:**
- Create: `supabase/functions/ai-proxy/index.ts`
- Create: `supabase/functions/ai-proxy/deno.json`
- Test: `supabase/functions/ai-proxy/index_test.ts`

- [ ] **Step 1: deno.json**

```json
{
  "tasks": { "test": "deno test --allow-env --allow-net" },
  "imports": {
    "std/": "https://deno.land/std@0.224.0/"
  }
}
```

- [ ] **Step 2: Write failing test for op routing**

`index_test.ts`:
```ts
import { assertEquals } from "std/assert/mod.ts";
import { handle } from "./index.ts";

// Fakes injected via the deps object
const deps = {
  verifyIntegrity: async () => true,
  callGemini: async (_op: string, _img: string, _prompt?: string) => "GEM_B64",
  callOpenRouter: async (_img: string) => "a caption",
};

function req(body: unknown): Request {
  return new Request("http://x/ai-proxy", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

Deno.test("describe routes to OpenRouter and returns text", async () => {
  const res = await handle(req({ op: "describe", image_b64: "X", integrity_token: "T" }), deps);
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { text: "a caption" });
});

Deno.test("upscale routes to Gemini and returns image", async () => {
  const res = await handle(req({ op: "upscale", image_b64: "X", integrity_token: "T" }), deps);
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { image_b64: "GEM_B64" });
});

Deno.test("unknown op is 400", async () => {
  const res = await handle(req({ op: "bogus", image_b64: "X", integrity_token: "T" }), deps);
  assertEquals(res.status, 400);
});

Deno.test("failed attestation is 401", async () => {
  const res = await handle(
    req({ op: "upscale", image_b64: "X", integrity_token: "T" }),
    { ...deps, verifyIntegrity: async () => false },
  );
  assertEquals(res.status, 401);
});
```

- [ ] **Step 3: Run test, verify fail**

Run: `cd supabase/functions/ai-proxy && deno test --allow-env --allow-net`
Expected: FAIL — `Module not found "./index.ts"` / `handle` not exported.

- [ ] **Step 4: Implement index.ts**

```ts
export interface Deps {
  verifyIntegrity: (token: string, requestHash: string | undefined) => Promise<boolean>;
  callGemini: (op: string, imageB64: string, prompt?: string, maskB64?: string) => Promise<string>;
  callOpenRouter: (imageB64: string) => Promise<string>;
}

interface Body {
  op?: string;
  image_b64?: string;
  mask_b64?: string;
  prompt?: string;
  request_hash?: string;
  integrity_token?: string;
}

const GEMINI_OPS = new Set(["upscale", "inpaint", "prompt_edit", "generate"]);

const json = (status: number, obj: unknown) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

export async function handle(reqObj: Request, deps: Deps): Promise<Response> {
  if (reqObj.method !== "POST") return json(405, { error: "method" });
  let body: Body;
  try { body = await reqObj.json(); } catch { return json(400, { error: "bad_json" }); }

  const { op, image_b64, mask_b64, prompt, request_hash, integrity_token } = body;
  if (!op || (!image_b64 && op !== "generate")) return json(400, { error: "missing_fields" });
  if (!integrity_token) return json(401, { error: "no_token" });

  const ok = await deps.verifyIntegrity(integrity_token, request_hash);
  if (!ok) return json(401, { error: "attestation_failed" });

  try {
    if (op === "describe") {
      const text = await deps.callOpenRouter(image_b64 ?? "");
      return json(200, { text });
    }
    if (GEMINI_OPS.has(op)) {
      const img = await deps.callGemini(op, image_b64 ?? "", prompt, mask_b64);
      return json(200, { image_b64: img });
    }
    return json(400, { error: "unknown_op" });
  } catch (e) {
    const msg = (e as Error).message;
    if (msg === "quota") return json(429, { error: "quota" });
    return json(502, { error: "failure", detail: msg });
  }
}
```

- [ ] **Step 5: Run test, verify pass**

Run: `deno test --allow-env --allow-net`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add supabase/functions/ai-proxy/index.ts supabase/functions/ai-proxy/index_test.ts supabase/functions/ai-proxy/deno.json
git commit -m "feat(proxy): ai-proxy op routing with injectable deps"
```

---

## Task 2: Provider calls (Gemini + OpenRouter)

**Files:**
- Create: `supabase/functions/ai-proxy/providers.ts`
- Test: `supabase/functions/ai-proxy/providers_test.ts`

- [ ] **Step 1: Write failing test (mock fetch)**

`providers_test.ts`:
```ts
import { assertEquals, assertRejects } from "std/assert/mod.ts";
import { makeGemini, makeOpenRouter } from "./providers.ts";

const origFetch = globalThis.fetch;
function mockFetch(status: number, body: unknown) {
  globalThis.fetch = async () =>
    new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
function restore() { globalThis.fetch = origFetch; }

Deno.test("gemini parses camelCase inlineData", async () => {
  mockFetch(200, { candidates: [{ content: { parts: [{ inlineData: { mimeType: "image/png", data: "IMG" } }] } }] });
  try {
    const gemini = makeGemini("KEY");
    assertEquals(await gemini("upscale", "SRC"), "IMG");
  } finally { restore(); }
});

Deno.test("gemini 429 throws quota", async () => {
  mockFetch(429, { error: "rate" });
  try {
    const gemini = makeGemini("KEY");
    await assertRejects(() => gemini("upscale", "SRC"), Error, "quota");
  } finally { restore(); }
});

Deno.test("openrouter parses choices content", async () => {
  mockFetch(200, { choices: [{ message: { content: "  a cat  " } }] });
  try {
    const desc = makeOpenRouter("KEY");
    assertEquals(await desc("SRC"), "a cat");
  } finally { restore(); }
});
```

- [ ] **Step 2: Run, verify fail**

Run: `deno test providers_test.ts --allow-net`
Expected: FAIL — `./providers.ts` not found.

- [ ] **Step 3: Implement providers.ts**

```ts
const GEMINI_MODEL = "gemini-2.5-flash-image";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const OPENROUTER_MODEL = "meta-llama/llama-3.2-11b-vision-instruct:free";

const ERASE = "Remove the masked subject and fill the area naturally with surrounding texture.";
const UPSCALE = "Upscale this image 2x preserving fine detail. Do not change content.";

export function makeGemini(apiKey: string) {
  return async (op: string, imageB64: string, prompt?: string, maskB64?: string): Promise<string> => {
    const parts: unknown[] = [];
    if (op === "generate") {
      parts.push({ text: prompt ?? "" });
    } else if (op === "inpaint") {
      parts.push({ text: `Edit the first image. The second image is a white-mask indicating the region to modify. ${prompt ?? ERASE}` });
      parts.push({ inline_data: { mime_type: "image/jpeg", data: imageB64 } });
      if (maskB64) parts.push({ inline_data: { mime_type: "image/jpeg", data: maskB64 } });
    } else {
      const text = op === "upscale" ? UPSCALE : (prompt ?? "");
      parts.push({ text });
      parts.push({ inline_data: { mime_type: "image/jpeg", data: imageB64 } });
    }
    const payload = {
      contents: [{ parts }],
      generationConfig: { response_modalities: ["IMAGE"] },
    };
    const resp = await fetch(`${GEMINI_URL}?key=${apiKey}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (resp.status === 429) throw new Error("quota");
    if (!resp.ok) throw new Error(`gemini_http_${resp.status}`);
    const data = await resp.json();
    const part = data?.candidates?.[0]?.content?.parts?.find((p: { inlineData?: { data?: string } }) => p?.inlineData?.data);
    const b64 = part?.inlineData?.data;
    if (!b64) throw new Error("no_image");
    return b64;
  };
}

export function makeOpenRouter(apiKey: string) {
  return async (imageB64: string): Promise<string> => {
    const payload = {
      model: OPENROUTER_MODEL,
      messages: [{
        role: "user",
        content: [
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${imageB64}` } },
          { type: "text", text: "Describe this photo in 2-3 sentences. Be specific about the subjects, setting, and mood." },
        ],
      }],
    };
    const resp = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "authorization": `Bearer ${apiKey}`,
        "http-referer": "com.webstudio.lumagallery",
      },
      body: JSON.stringify(payload),
    });
    if (resp.status === 429) throw new Error("quota");
    if (!resp.ok) throw new Error(`openrouter_http_${resp.status}`);
    const data = await resp.json();
    const content = data?.choices?.[0]?.message?.content;
    if (!content) throw new Error("no_content");
    return String(content).trim();
  };
}
```

- [ ] **Step 4: Run, verify pass**

Run: `deno test providers_test.ts --allow-net`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add supabase/functions/ai-proxy/providers.ts supabase/functions/ai-proxy/providers_test.ts
git commit -m "feat(proxy): Gemini + OpenRouter provider calls"
```

---

## Task 3: Play Integrity verification

**Files:**
- Create: `supabase/functions/ai-proxy/integrity.ts`
- Test: `supabase/functions/ai-proxy/integrity_test.ts`

- [ ] **Step 1: Write failing test for verdict checking (pure function)**

`integrity_test.ts`:
```ts
import { assertEquals } from "std/assert/mod.ts";
import { checkVerdict } from "./integrity.ts";

const PKG = "com.webstudio.lumagallery";

Deno.test("accepts PLAY_RECOGNIZED with matching package + hash", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), true);
});

Deno.test("rejects wrong package", () => {
  const payload = {
    requestDetails: { requestPackageName: "com.evil", requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.evil" },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});

Deno.test("rejects unrecognized app", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});

Deno.test("rejects request hash mismatch", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "OTHER" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});
```

- [ ] **Step 2: Run, verify fail**

Run: `deno test integrity_test.ts`
Expected: FAIL — `./integrity.ts` not found.

- [ ] **Step 3: Implement integrity.ts**

```ts
interface VerdictPayload {
  requestDetails?: { requestPackageName?: string; requestHash?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string };
}

export function checkVerdict(payload: VerdictPayload, expectedPkg: string, expectedHash?: string): boolean {
  const ai = payload.appIntegrity;
  const rd = payload.requestDetails;
  if (!ai || !rd) return false;
  if (ai.appRecognitionVerdict !== "PLAY_RECOGNIZED") return false;
  if (ai.packageName !== expectedPkg) return false;
  if (rd.requestPackageName !== expectedPkg) return false;
  if (expectedHash !== undefined && rd.requestHash !== expectedHash) return false;
  return true;
}

// --- Google OAuth (service account → access token) ---
interface ServiceAccount { client_email: string; private_key: string; }

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "").replace(/\s/g, "");
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

function b64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64url(new TextEncoder().encode(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/playintegrity",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600,
  })));
  const signingInput = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(signingInput)));
  const jwt = `${signingInput}.${b64url(sig)}`;
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  if (!resp.ok) throw new Error(`oauth_${resp.status}`);
  return (await resp.json()).access_token;
}

// Real verifier used in production (index.ts wires this when not testing).
export function makeVerifier(saJson: string, pkg: string) {
  const sa: ServiceAccount = JSON.parse(saJson);
  return async (token: string, requestHash?: string): Promise<boolean> => {
    try {
      const at = await accessToken(sa);
      const resp = await fetch(
        `https://playintegrity.googleapis.com/v1/${pkg}:decodeIntegrityToken`,
        {
          method: "POST",
          headers: { "content-type": "application/json", authorization: `Bearer ${at}` },
          body: JSON.stringify({ integrity_token: token }),
        },
      );
      if (!resp.ok) return false;
      const data = await resp.json();
      return checkVerdict(data?.tokenPayloadExternal ?? {}, pkg, requestHash);
    } catch {
      return false;
    }
  };
}
```

- [ ] **Step 4: Run, verify pass**

Run: `deno test integrity_test.ts`
Expected: PASS (4 tests). `makeVerifier`/OAuth paths are exercised only in production (network); `checkVerdict` is the unit-tested core.

- [ ] **Step 5: Wire production deps into index.ts**

Append to `index.ts`:
```ts
import { makeGemini, makeOpenRouter } from "./providers.ts";
import { makeVerifier } from "./integrity.ts";

if (import.meta.main) {
  const pkg = Deno.env.get("ANDROID_PACKAGE_NAME") ?? "com.webstudio.lumagallery";
  const verify = makeVerifier(Deno.env.get("PLAY_INTEGRITY_SA") ?? "{}", pkg);
  const gemini = makeGemini(Deno.env.get("GEMINI_API_KEY") ?? "");
  const openrouter = makeOpenRouter(Deno.env.get("OPENROUTER_API_KEY") ?? "");
  const deps: Deps = {
    verifyIntegrity: verify,
    callGemini: gemini,
    callOpenRouter: openrouter,
  };
  Deno.serve((r) => handle(r, deps));
}
```

- [ ] **Step 6: Run full suite**

Run: `deno test --allow-env --allow-net`
Expected: PASS (all tasks 1-3 tests).

- [ ] **Step 7: Commit**

```bash
git add supabase/functions/ai-proxy/integrity.ts supabase/functions/ai-proxy/integrity_test.ts supabase/functions/ai-proxy/index.ts
git commit -m "feat(proxy): Play Integrity verification + production wiring"
```

---

## Task 4: Deploy edge function + set secrets

**Files:** none (ops). Requires a Supabase project ref (create new or reuse — see spec prerequisites).

- [ ] **Step 1: Link project**

Run: `supabase link --project-ref <REF>`

- [ ] **Step 2: Set secrets**

```bash
supabase secrets set GEMINI_API_KEY="<real-gemini-key>"
supabase secrets set OPENROUTER_API_KEY="<rotated-openrouter-key>"
supabase secrets set ANDROID_PACKAGE_NAME="com.webstudio.lumagallery"
supabase secrets set PLAY_INTEGRITY_SA="$(cat service-account.json)"
```

- [ ] **Step 3: Deploy**

Run: `supabase functions deploy ai-proxy`
Expected: prints the function URL `https://<ref>.supabase.co/functions/v1/ai-proxy`.

- [ ] **Step 4: Smoke test (expect 401 — no valid token)**

```bash
curl -i -X POST https://<ref>.supabase.co/functions/v1/ai-proxy \
  -H "apikey: <anon>" -H "Authorization: Bearer <anon>" \
  -H "content-type: application/json" \
  -d '{"op":"describe","image_b64":"X","integrity_token":"bad"}'
```
Expected: `HTTP/2 401` `{"error":"attestation_failed"}` — proves attestation gate is live.

- [ ] **Step 5: Commit (no code; record URL in spec)**

Add the deployed URL under "Rollout" in the spec, then:
```bash
git add docs/superpowers/specs/2026-06-20-server-side-ai-keys-design.md
git commit -m "docs: record deployed ai-proxy URL"
```

---

## Task 5: Android — Gradle deps + BuildConfig fields

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `local.properties`

- [ ] **Step 1: Add dependencies**

In `app/build.gradle.kts` `dependencies { }`:
```kotlin
implementation("com.google.android.play:integrity:1.4.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: Swap BuildConfig fields**

In `app/build.gradle.kts`, replace the two AI-key lines (currently lines 17-18 reading `geminiApiKey`/`openRouterApiKey`, and buildConfigField lines 41-42) with:
```kotlin
// top, with other localProperties reads:
val supabaseFunctionUrl: String = localProperties.getProperty("SUPABASE_FUNCTION_URL", "")
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY", "")
val playCloudProjectNumber: String = localProperties.getProperty("PLAY_CLOUD_PROJECT_NUMBER", "0")
```
```kotlin
// in defaultConfig, replacing GEMINI_API_KEY / OPENROUTER_API_KEY buildConfigFields:
buildConfigField("String", "SUPABASE_FUNCTION_URL", "\"$supabaseFunctionUrl\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", "${playCloudProjectNumber}L")
```

- [ ] **Step 3: Update local.properties**

Remove `GEMINI_API_KEY` and `OPENROUTER_API_KEY` lines. Add:
```
SUPABASE_FUNCTION_URL=https://<ref>.supabase.co/functions/v1/ai-proxy
SUPABASE_ANON_KEY=<anon-key>
PLAY_CLOUD_PROJECT_NUMBER=<gcp-project-number>
```

- [ ] **Step 4: Sync (Android Studio: Sync Now). Verify build config generates.**

Expected: `BuildConfig.SUPABASE_FUNCTION_URL` resolvable; old key fields gone (compile errors in old clients — fixed in Task 8 by deleting them).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add Play Integrity deps, swap AI-key BuildConfig for Supabase fields"
```

---

## Task 6: Android — IntegrityTokenProvider

**Files:**
- Create: `app/src/main/java/com/webstudio/lumagallery/data/ai/IntegrityTokenProvider.kt`

- [ ] **Step 1: Implement interface + Play Integrity impl** (no unit test — Play Services can't run in JVM unit tests; verified via the fake in Task 7)

```kotlin
package com.webstudio.lumagallery.data.ai

import android.content.Context
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityManagerFactory
import kotlinx.coroutines.tasks.await

/** Returns a Play Integrity token bound to [requestHash]. */
interface IntegrityTokenProvider {
    suspend fun token(requestHash: String): String
}

class PlayIntegrityTokenProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenProvider {

    private val appContext = context.applicationContext
    @Volatile private var provider: StandardIntegrityTokenProvider? = null

    private suspend fun ensureProvider(): StandardIntegrityTokenProvider {
        provider?.let { return it }
        val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(appContext)
        val prepared = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build(),
        ).await()
        provider = prepared
        return prepared
    }

    override suspend fun token(requestHash: String): String {
        val p = ensureProvider()
        val response: StandardIntegrityToken = p.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build(),
        ).await()
        return response.token()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/webstudio/lumagallery/data/ai/IntegrityTokenProvider.kt
git commit -m "feat(ai): Play Integrity Standard token provider behind interface"
```

---

## Task 7: Android — AiProxyClient (TDD with MockWebServer)

**Files:**
- Create: `app/src/main/java/com/webstudio/lumagallery/data/ai/AiProxyClient.kt`
- Test: `app/src/test/java/com/webstudio/lumagallery/data/ai/AiProxyClientTest.kt`

- [ ] **Step 1: Write failing test**

`AiProxyClientTest.kt`:
```kotlin
package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AiProxyClientTest {
    private lateinit var server: MockWebServer
    private val fakeIntegrity = object : IntegrityTokenProvider {
        override suspend fun token(requestHash: String) = "FAKE_TOKEN"
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = AiProxyClient(
        functionUrl = server.url("/ai-proxy").toString(),
        anonKey = "anon",
        integrity = fakeIntegrity,
        http = OkHttpClient(),
        // encode a 1x1 bitmap to a tiny byte array deterministically
        encodeJpeg = { _, _ -> byteArrayOf(1, 2, 3) },
        decode = { _ -> FAKE_BITMAP },
    )

    @Test fun describe_returns_text() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"a cat"}""").setResponseCode(200))
        assertEquals("a cat", client().describe(FAKE_BITMAP))
    }

    @Test fun edit_returns_bitmap() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"image_b64":"AQID"}""").setResponseCode(200))
        val out = client().editImage("upscale", FAKE_BITMAP, null, null)
        assertEquals(FAKE_BITMAP, out)
    }

    @Test fun http_429_throws_quota() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"quota"}"""))
        assertThrows(AiProxyClient.QuotaException::class.java) {
            runBlocking { client().describe(FAKE_BITMAP) }
        }
    }

    @Test fun http_401_throws_attestation() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"attestation_failed"}"""))
        assertThrows(AiProxyClient.AttestationException::class.java) {
            runBlocking { client().describe(FAKE_BITMAP) }
        }
    }

    companion object {
        // Bitmap is not used by encode/decode here (both faked); a non-null stub suffices.
        private val FAKE_BITMAP: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run (Android Studio): `AiProxyClientTest` → Run.
Expected: FAIL — `AiProxyClient` unresolved.

- [ ] **Step 3: Implement AiProxyClient.kt**

```kotlin
package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiProxyClient(
    private val functionUrl: String,
    private val anonKey: String,
    private val integrity: IntegrityTokenProvider,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val encodeJpeg: (Bitmap, Int) -> ByteArray = ::defaultEncode,
    private val decode: (ByteArray) -> Bitmap? = ::defaultDecode,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun editImage(op: String, src: Bitmap, mask: Bitmap?, prompt: String?): Bitmap =
        withContext(Dispatchers.IO) {
            val imgB64 = b64(encodeJpeg(src, 90))
            val maskB64 = mask?.let { b64(encodeJpeg(it, 90)) }
            val hash = requestHash(op, imgB64)
            val token = integrity.token(hash)
            val body = ProxyRequest(op, imgB64, maskB64, prompt, hash, token)
            val resp = post(body)
            val parsed = json.decodeFromString(ImageResponse.serializer(), resp)
            val bytes = Base64.decode(parsed.imageB64 ?: throw IOException("no image"), Base64.DEFAULT)
            decode(bytes) ?: throw IOException("decode failed")
        }

    suspend fun describe(src: Bitmap): String = withContext(Dispatchers.IO) {
        val imgB64 = b64(encodeJpeg(src, 85))
        val hash = requestHash("describe", imgB64)
        val token = integrity.token(hash)
        val resp = post(ProxyRequest("describe", imgB64, null, null, hash, token))
        json.decodeFromString(TextResponse.serializer(), resp).text?.trim()
            ?: throw IOException("no text")
    }

    private fun post(body: ProxyRequest): String {
        val reqBody = json.encodeToString(ProxyRequest.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(functionUrl)
            .post(reqBody)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .build()
        http.newCall(request).execute().use { resp ->
            when {
                resp.code == 401 -> throw AttestationException()
                resp.code == 429 -> throw QuotaException()
                !resp.isSuccessful -> throw IOException("proxy HTTP ${resp.code}")
            }
            return resp.body?.string() ?: throw IOException("empty body")
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun requestHash(op: String, imgB64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((op + imgB64).toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    class QuotaException : IOException("quota")
    class AttestationException : IOException("attestation failed")

    @Serializable
    private data class ProxyRequest(
        val op: String,
        @SerialName("image_b64") val imageB64: String,
        @SerialName("mask_b64") val maskB64: String? = null,
        val prompt: String? = null,
        @SerialName("request_hash") val requestHash: String,
        @SerialName("integrity_token") val integrityToken: String,
    )

    @Serializable
    private data class ImageResponse(@SerialName("image_b64") val imageB64: String? = null)

    @Serializable
    private data class TextResponse(val text: String? = null)

    companion object {
        private fun defaultEncode(bmp: Bitmap, quality: Int): ByteArray {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            return bos.toByteArray()
        }
        private fun defaultDecode(bytes: ByteArray): Bitmap? =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `AiProxyClientTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/webstudio/lumagallery/data/ai/AiProxyClient.kt app/src/test/java/com/webstudio/lumagallery/data/ai/AiProxyClientTest.kt
git commit -m "feat(ai): AiProxyClient with attestation + error mapping (TDD)"
```

---

## Task 8: Android — refactor AiEditRepository, delete old clients

**Files:**
- Modify: `app/src/main/java/com/webstudio/lumagallery/data/ai/AiEditRepository.kt`
- Delete: `GeminiImageClient.kt`, `OpenRouterVisionClient.kt`

- [ ] **Step 1: Rewrite AiEditRepository.kt**

```kotlin
package com.webstudio.lumagallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.webstudio.lumagallery.BuildConfig
import java.io.IOException
import java.net.UnknownHostException

class AiEditRepository(
    context: Context,
    private val bgRemover: OnDeviceBgRemover = OnDeviceBgRemover(),
    private val proxy: AiProxyClient = AiProxyClient(
        functionUrl = BuildConfig.SUPABASE_FUNCTION_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        integrity = PlayIntegrityTokenProvider(context, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER),
    ),
) {
    val aiEnabled: Boolean get() = BuildConfig.SUPABASE_FUNCTION_URL.isNotBlank()

    suspend fun removeBackground(src: Bitmap): AiResult = runOp { bgRemover.removeBackground(src) }

    suspend fun upscale(src: Bitmap): AiResult = cloud { proxy.editImage("upscale", src, null, null) }

    suspend fun eraseObject(src: Bitmap, mask: Bitmap, prompt: String = DEFAULT_ERASE_PROMPT): AiResult =
        cloud { proxy.editImage("inpaint", src, mask, prompt) }

    suspend fun promptEdit(src: Bitmap, prompt: String): AiResult =
        cloud { proxy.editImage("prompt_edit", src, null, prompt) }

    suspend fun generateImage(prompt: String): AiResult =
        cloud { proxy.editImage("generate", BLANK, null, prompt) }

    suspend fun describePhoto(src: Bitmap): AiResult {
        if (!aiEnabled) return AiResult.MissingApiKey
        return try {
            AiResult.TextSuccess(proxy.describe(src))
        } catch (e: AiProxyClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: AiProxyClient.AttestationException) {
            AiResult.Failure("device verification failed")
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "AI proxy failed")
        }
    }

    private suspend fun cloud(op: suspend () -> Bitmap): AiResult {
        if (!aiEnabled) return AiResult.MissingApiKey
        return try {
            AiResult.Success(op())
        } catch (e: AiProxyClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: AiProxyClient.AttestationException) {
            AiResult.Failure("device verification failed")
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "AI proxy failed")
        }
    }

    private suspend fun runOp(block: suspend () -> Bitmap): AiResult = try {
        AiResult.Success(block())
    } catch (e: Exception) {
        Log.d(TAG, "AI op failed", e)
        AiResult.Failure(e.message ?: "AI op failed")
    }

    companion object {
        private const val TAG = "AiEditRepository"
        private const val DEFAULT_ERASE_PROMPT =
            "Remove the masked subject and fill the area naturally with surrounding texture."
        private val BLANK: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
```

> Note: `generate` sends a 1x1 `BLANK` bitmap; the function ignores `image_b64` for `op=generate`.

- [ ] **Step 2: Delete obsolete clients**

```bash
git rm app/src/main/java/com/webstudio/lumagallery/data/ai/GeminiImageClient.kt \
       app/src/main/java/com/webstudio/lumagallery/data/ai/OpenRouterVisionClient.kt
```

- [ ] **Step 3: Fix AiEditRepository construction site**

Find where `AiEditRepository()` is instantiated (likely `GalleryViewModel` / `EditViewModel`). It now needs a `Context`. In each ViewModel that is an `AndroidViewModel`, pass `getApplication()`; otherwise pass the activity/application context already available. Search:
Run (Android Studio Find): `AiEditRepository(` — update each call to `AiEditRepository(context)`.

- [ ] **Step 4: Build (Android Studio). Verify compiles** (UI flag refs still break — fixed Task 9).

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/webstudio/lumagallery/data/ai/
git commit -m "refactor(ai): route AiEditRepository through proxy, delete key-holding clients"
```

---

## Task 9: Android — swap `hasGeminiKey`/`hasOpenRouterKey` → `aiEnabled`

**Files:**
- Modify: `ui/viewmodel/GalleryViewModel.kt`, `ui/viewmodel/EditViewModel.kt`,
  `ui/screens/edit/panels/AiPanel.kt`, `ui/screens/edit/EditScreen.kt`, `ui/screens/GalleryScreen.kt`

- [ ] **Step 1: GalleryViewModel** — replace
```kotlin
val hasGeminiKey: Boolean get() = aiRepo.hasGeminiKey
```
with
```kotlin
val aiEnabled: Boolean get() = aiRepo.aiEnabled
```

- [ ] **Step 2: EditViewModel** — in `EditUiState` replace `val hasGeminiKey: Boolean = false` with `val aiEnabled: Boolean = false`; at init (currently line ~48) replace
```kotlin
hasGeminiKey = com.webstudio.lumagallery.BuildConfig.GEMINI_API_KEY.isNotBlank()
```
with
```kotlin
aiEnabled = com.webstudio.lumagallery.BuildConfig.SUPABASE_FUNCTION_URL.isNotBlank()
```

- [ ] **Step 3: AiPanel.kt** — change param `hasGeminiKey: Boolean` → `aiEnabled: Boolean`; update `val hasCloud = hasGeminiKey` → `val hasCloud = aiEnabled`.

- [ ] **Step 4: EditScreen.kt** — update the call site `hasGeminiKey = state.hasGeminiKey,` → `aiEnabled = state.aiEnabled,`.

- [ ] **Step 5: GalleryScreen.kt** — param `hasGeminiKey: Boolean = false` → `aiEnabled: Boolean = false`; update `if (!isSelectionMode && hasGeminiKey)` → `if (!isSelectionMode && aiEnabled)`; update the caller in `MainActivity`/navigation passing `hasGeminiKey =` to `aiEnabled =`.

- [ ] **Step 6: Build + run existing tests (Android Studio)**

Expected: project compiles; `GalleryScreenTest` and other existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/webstudio/lumagallery/ui/
git commit -m "refactor(ui): replace per-provider key flags with single aiEnabled"
```

---

## Task 10: Manifest + docs

**Files:**
- Modify: `CLAUDE.md`, `README.md`

- [ ] **Step 1: CLAUDE.md** — in the AI-layer section, replace the API-key bullet (currently mentions `GEMINI_API_KEY`/`OPENROUTER_API_KEY` in `local.properties`→`BuildConfig`) with:
```
- AI keys live **server-side** in a Supabase Edge Function (`supabase/functions/ai-proxy`). The app ships only `SUPABASE_FUNCTION_URL` + `SUPABASE_ANON_KEY` (publishable) + `PLAY_CLOUD_PROJECT_NUMBER`. Cloud ops go App → `AiProxyClient` (attaches a Play Integrity token) → `ai-proxy` (verifies attestation, attaches `GEMINI_API_KEY`/`OPENROUTER_API_KEY` from Supabase secrets, forwards to Gemini/OpenRouter). On-device background removal (`OnDeviceBgRemover`) needs no key/proxy. UI gates on `aiEnabled` (= `SUPABASE_FUNCTION_URL.isNotBlank()`), not per-provider key flags.
```

- [ ] **Step 2: README.md** — under Setup, replace the AI-key instructions with the Supabase function URL + anon key + cloud project number lines.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document server-side AI proxy architecture"
```

---

## Task 11: End-to-end verification (real device)

- [ ] **Step 1:** Install debug build on a device with Play Store + Google account (Play Integrity requires it). Register a license tester in Play Console for the app.
- [ ] **Step 2:** In the editor, run **Describe photo** (cheapest op). Expected: caption returned. If `401` device verification → check Play Console linkage + `PLAY_CLOUD_PROJECT_NUMBER`.
- [ ] **Step 3:** Run **Upscale**. Expected: edited image returns.
- [ ] **Step 4:** Confirm no key in APK: `apktool d app-debug.apk && grep -ri "AIza\|sk-or-v1" .` → expect **no matches** (Gemini keys start `AIza`, OpenRouter `sk-or-v1`).
- [ ] **Step 5:** Rotate the old OpenRouter key in the OpenRouter dashboard (the previously-committed one is compromised). Confirm proxy still works with the rotated key set as a Supabase secret.

---

## Self-Review Notes

- **Spec coverage:** proxy (T1-3), attestation (T3,T6), deploy/secrets (T4), client (T5-7), repo refactor + client deletion (T8), capability-flag swap (T9), config removal (T5,T10), docs (T10), OpenRouter rotation (T4 secret, T11.5), Gemini camelCase + model fix (T2). All spec sections mapped.
- **Type consistency:** `editImage(op, src, mask?, prompt?)` / `describe(src)` used identically in `AiProxyClient`, its test, and `AiEditRepository`. `QuotaException`/`AttestationException` consistent across client + repo. `aiEnabled` consistent across repo/VMs/screens.
- **Known limitation:** `IntegrityTokenProvider` real impl + edge-function OAuth/decode paths are not JVM/Deno unit-tested (require Play Services / live Google APIs); verified in T11 end-to-end. Core logic (`checkVerdict`, op routing, provider parsing, client error mapping) is unit-tested.
