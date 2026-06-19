import { assertEquals } from "std/assert/mod.ts";
import { checkVerdict } from "./integrity.ts";

const PKG = "com.webstudio.lumagallery";

Deno.test("accepts PLAY_RECOGNIZED with matching package + hash", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), true);
});

Deno.test("rejects wrong package", () => {
  const payload = {
    requestDetails: { requestPackageName: "com.evil", requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.evil" },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});

Deno.test("rejects unrecognized app", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "H" },
    appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});

Deno.test("rejects request hash mismatch", () => {
  const payload = {
    requestDetails: { requestPackageName: PKG, requestHash: "OTHER" },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
  };
  assertEquals(checkVerdict(payload, PKG, "H"), false);
});
