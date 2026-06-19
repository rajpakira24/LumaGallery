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
