/**
 * Multi-window fixed-window rate limiter backed by Deno KV.
 * Each window is an independent fixed bucket keyed by (id, name, windowIndex).
 * A request is allowed only if it is under the limit in EVERY window.
 * When blocked, retryAfterSec = the longest wait among the exceeded windows.
 * `remaining`/`resetSec` are per-window (by name) so callers can surface usage.
 */
export interface RateWindow {
  name: string;
  windowSec: number;
  limit: number;
}

export interface RateResult {
  allowed: boolean;
  retryAfterSec: number;
  remaining: Record<string, number>; // per window: requests left after this one
  resetSec: Record<string, number>; // per window: seconds until it resets
}

export function makeRateLimiter(kv: Deno.Kv, windows: RateWindow[]) {
  return async (id: string): Promise<RateResult> => {
    const now = Date.now();
    const reads: { w: RateWindow; key: Deno.KvKey; count: bigint }[] = [];
    const remaining: Record<string, number> = {};
    const resetSec: Record<string, number> = {};
    let retryAfter = 0;

    // Pass 1: read every window, compute resets, detect any exceeded window.
    for (const w of windows) {
      const idx = Math.floor(now / (w.windowSec * 1000));
      const key = ["rl", id, w.name, idx];
      const entry = await kv.get<Deno.KvU64>(key);
      const count = entry.value?.value ?? 0n;
      reads.push({ w, key, count });
      resetSec[w.name] = Math.ceil(((idx + 1) * w.windowSec * 1000 - now) / 1000);
      if (count >= BigInt(w.limit)) retryAfter = Math.max(retryAfter, resetSec[w.name]);
    }

    if (retryAfter > 0) {
      for (const r of reads) remaining[r.w.name] = Math.max(0, r.w.limit - Number(r.count));
      return { allowed: false, retryAfterSec: retryAfter, remaining, resetSec };
    }

    // Pass 2: increment every window; remaining reflects the post-increment count.
    for (const r of reads) {
      await kv.atomic()
        .set(r.key, new Deno.KvU64(r.count + 1n), { expireIn: (r.w.windowSec + 5) * 1000 })
        .commit();
      remaining[r.w.name] = Math.max(0, r.w.limit - Number(r.count) - 1);
    }
    return { allowed: true, retryAfterSec: 0, remaining, resetSec };
  };
}
