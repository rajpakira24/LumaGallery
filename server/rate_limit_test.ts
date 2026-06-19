import { assertEquals } from "std/assert/mod.ts";
import { makeRateLimiter } from "./rate_limit.ts";

Deno.test("allows up to N per window then blocks, isolated per id", async () => {
  const kv = await Deno.openKv(":memory:");
  try {
    const limit = makeRateLimiter(kv, 3);
    assertEquals(await limit("ip1"), true);
    assertEquals(await limit("ip1"), true);
    assertEquals(await limit("ip1"), true);
    assertEquals(await limit("ip1"), false); // 4th blocked
    assertEquals(await limit("ip2"), true); // different id unaffected
  } finally {
    kv.close();
  }
});
