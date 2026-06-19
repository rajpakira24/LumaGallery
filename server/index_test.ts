import { assertEquals } from "std/assert/mod.ts";
import { handle } from "./index.ts";

const ALLOW = async () => ({ allowed: true, retryAfterSec: 0 });

// Fakes injected via the deps object
const deps = {
  checkGeneral: ALLOW,
  checkImage: ALLOW,
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

Deno.test("general rate limit returns 429 with retry_after + header", async () => {
  const res = await handle(
    req({ op: "describe", image_b64: "X", integrity_token: "T" }),
    { ...deps, checkGeneral: async () => ({ allowed: false, retryAfterSec: 42 }) },
  );
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("retry-after"), "42");
  assertEquals(await res.json(), { error: "rate_limited", retry_after: 42 });
});

Deno.test("image op over image-day budget returns 429 (after attestation)", async () => {
  const res = await handle(
    req({ op: "upscale", image_b64: "X", integrity_token: "T" }),
    { ...deps, checkImage: async () => ({ allowed: false, retryAfterSec: 3600 }) },
  );
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("retry-after"), "3600");
});

Deno.test("describe is NOT subject to the image-day budget", async () => {
  // checkImage denies, but describe is not an image op → still succeeds
  const res = await handle(
    req({ op: "describe", image_b64: "X", integrity_token: "T" }),
    { ...deps, checkImage: async () => ({ allowed: false, retryAfterSec: 9999 }) },
  );
  assertEquals(res.status, 200);
});
