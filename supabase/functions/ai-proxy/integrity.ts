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
