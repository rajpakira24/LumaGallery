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
