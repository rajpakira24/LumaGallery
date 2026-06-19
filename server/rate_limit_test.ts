import { assertEquals } from "std/assert/mod.ts";
import { makeRateLimiter } from "./rate_limit.ts";

Deno.test("allows up to N per window then blocks, isolated per id", async () => {
  const kv = await Deno.openKv(":memory:");
  try {
    const limit = makeRateLimiter(kv, [{ name: "min", windowSec: 60, limit: 3 }]);
    assertEquals((await limit("ip1")).allowed, true);
    assertEquals((await limit("ip1")).allowed, true);
    assertEquals((await limit("ip1")).allowed, true);
    const blocked = await limit("ip1");
    assertEquals(blocked.allowed, false); // 4th blocked
    assertEquals(blocked.retryAfterSec > 0 && blocked.retryAfterSec <= 60, true);
    assertEquals((await limit("ip2")).allowed, true); // different id unaffected
  } finally {
    kv.close();
  }
});

Deno.test("multi-window: tightest window binds; retry-after = slowest exceeded window", async () => {
  const kv = await Deno.openKv(":memory:");
  try {
    // minute allows 5, hour allows 2 -> hour binds at the 3rd request
    const limit = makeRateLimiter(kv, [
      { name: "min", windowSec: 60, limit: 5 },
      { name: "hr", windowSec: 3600, limit: 2 },
    ]);
    assertEquals((await limit("ip")).allowed, true);
    assertEquals((await limit("ip")).allowed, true);
    const blocked = await limit("ip");
    assertEquals(blocked.allowed, false);
    // hour window is the exceeded one -> retry-after is on the hour scale (> 60)
    assertEquals(blocked.retryAfterSec > 60 && blocked.retryAfterSec <= 3600, true);
  } finally {
    kv.close();
  }
});
