import { makeGemini, makeOpenRouter } from "./providers.ts";
import { makeVerifier } from "./integrity.ts";
import { makeRateLimiter, type RateResult, type RateWindow } from "./rate_limit.ts";

export interface Deps {
  // General per-IP limit (all ops). Cheap: checked before body parse.
  checkGeneral: (id: string) => Promise<RateResult>;
  // Extra per-IP limit for paid image ops (Gemini). Checked after attestation.
  checkImage: (id: string) => Promise<RateResult>;
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

const rateLimited = (retryAfterSec: number) =>
  new Response(JSON.stringify({ error: "rate_limited", retry_after: retryAfterSec }), {
    status: 429,
    headers: { "content-type": "application/json", "retry-after": String(retryAfterSec) },
  });

export async function handle(reqObj: Request, deps: Deps): Promise<Response> {
  if (reqObj.method !== "POST") return json(405, { error: "method" });

  const ip = reqObj.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const general = await deps.checkGeneral(ip);
  if (!general.allowed) return rateLimited(general.retryAfterSec);

  let body: Body;
  try { body = await reqObj.json(); } catch { return json(400, { error: "bad_json" }); }

  const { op, image_b64, mask_b64, prompt, request_hash, integrity_token } = body;
  if (!op || (!image_b64 && op !== "generate")) return json(400, { error: "missing_fields" });
  if (!integrity_token) return json(401, { error: "no_token" });

  const ok = await deps.verifyIntegrity(integrity_token, request_hash);
  if (!ok) return json(401, { error: "attestation_failed" });

  // Paid image ops carry an extra, stricter daily budget — only consumed by
  // attested requests that are actually about to hit Gemini.
  if (GEMINI_OPS.has(op)) {
    const image = await deps.checkImage(ip);
    if (!image.allowed) return rateLimited(image.retryAfterSec);
  }

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

if (import.meta.main) {
  const pkg = Deno.env.get("ANDROID_PACKAGE_NAME") ?? "com.webstudio.lumagallery";
  // DEV ONLY: set ATTEST_MODE=allow_all to bypass Play Integrity for smoke testing
  // before a service account is configured. NEVER set this in production — it makes
  // the proxy an open door to the AI keys.
  const allowAll = Deno.env.get("ATTEST_MODE") === "allow_all";
  const verify = allowAll
    ? (async () => true)
    : makeVerifier(Deno.env.get("PLAY_INTEGRITY_SA") ?? "{}", pkg);
  if (allowAll) console.warn("[ai-proxy] ATTEST_MODE=allow_all — attestation DISABLED (dev only)");
  const gemini = makeGemini(Deno.env.get("GEMINI_API_KEY") ?? "");
  const openrouter = makeOpenRouter(Deno.env.get("OPENROUTER_API_KEY") ?? "");

  const num = (k: string, d: number) => Number(Deno.env.get(k) ?? String(d));
  const generalWindows: RateWindow[] = [
    { name: "min", windowSec: 60, limit: num("RATE_PER_MIN", 5) },
    { name: "hr", windowSec: 3600, limit: num("RATE_PER_HOUR", 50) },
    { name: "day", windowSec: 86400, limit: num("RATE_PER_DAY", 200) },
  ];
  const imageWindows: RateWindow[] = [
    { name: "img_day", windowSec: 86400, limit: num("RATE_IMAGE_PER_DAY", 20) },
  ];

  const kv = await Deno.openKv();
  const checkGeneral = makeRateLimiter(kv, generalWindows);
  const checkImage = makeRateLimiter(kv, imageWindows);
  const deps: Deps = {
    checkGeneral,
    checkImage,
    verifyIntegrity: verify,
    callGemini: gemini,
    callOpenRouter: openrouter,
  };
  Deno.serve((r) => handle(r, deps));
}
