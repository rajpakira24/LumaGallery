/**
 * Fixed-window per-id rate limiter backed by Deno KV.
 * One counter per (id, minute-window). Counters auto-expire after 2 minutes.
 * `id` is the caller's IP (best available identity for a login-less app).
 */
export function makeRateLimiter(kv: Deno.Kv, perMinute: number) {
  return async (id: string): Promise<boolean> => {
    const window = Math.floor(Date.now() / 60_000);
    const key = ["rl", id, window];
    const entry = await kv.get<Deno.KvU64>(key);
    const count = entry.value?.value ?? 0n;
    if (count >= BigInt(perMinute)) return false;
    const res = await kv.atomic()
      .check(entry)
      .set(key, new Deno.KvU64(count + 1n), { expireIn: 120_000 })
      .commit();
    if (!res.ok) {
      // lost the race against a concurrent request; re-read and decide
      const again = await kv.get<Deno.KvU64>(key);
      return (again.value?.value ?? 0n) < BigInt(perMinute);
    }
    return true;
  };
}
