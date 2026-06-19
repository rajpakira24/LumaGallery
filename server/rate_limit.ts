/**
 * Multi-window fixed-window rate limiter backed by Deno KV.
 * Each window is an independent fixed bucket keyed by (id, name, windowIndex).
 * A request is allowed only if it is under the limit in EVERY window.
 * When blocked, retryAfterSec = the longest wait among the exceeded windows
 * (you must wait for the slowest-resetting exceeded window).
 */
export interface RateWindow {
  name: string;
  windowSec: number;
  limit: number;
}

export interface RateResult {
  allowed: boolean;
  retryAfterSec: number;
}

export function makeRateLimiter(kv: Deno.Kv, windows: RateWindow[]) {
  return async (id: string): Promise<RateResult> => {
    const now = Date.now();
    const reads: { w: RateWindow; key: Deno.KvKey; count: bigint }[] = [];
    let retryAfter = 0;

    // First pass: read every window; if any is already at limit, deny (don't increment).
    for (const w of windows) {
      const idx = Math.floor(now / (w.windowSec * 1000));
      const key = ["rl", id, w.name, idx];
      const entry = await kv.get<Deno.KvU64>(key);
      const count = entry.value?.value ?? 0n;
      reads.push({ w, key, count });
      if (count >= BigInt(w.limit)) {
        const resetMs = (idx + 1) * w.windowSec * 1000 - now;
        retryAfter = Math.max(retryAfter, Math.ceil(resetMs / 1000));
      }
    }
    if (retryAfter > 0) return { allowed: false, retryAfterSec: retryAfter };

    // Second pass: increment every window (best-effort; small races are acceptable here).
    for (const r of reads) {
      await kv.atomic()
        .set(r.key, new Deno.KvU64(r.count + 1n), { expireIn: (r.w.windowSec + 5) * 1000 })
        .commit();
    }
    return { allowed: true, retryAfterSec: 0 };
  };
}
