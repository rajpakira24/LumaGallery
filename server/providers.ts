const GEMINI_MODEL = "gemini-2.5-flash-image";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const OPENROUTER_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free";

const ERASE = "Remove the masked subject and fill the area naturally with surrounding texture.";
const UPSCALE = "Upscale this image 2x preserving fine detail. Do not change content.";

export function makeGemini(apiKey: string) {
  return async (op: string, imageB64: string, prompt?: string, maskB64?: string): Promise<string> => {
    const parts: unknown[] = [];
    if (op === "generate") {
      parts.push({ text: prompt ?? "" });
    } else if (op === "inpaint") {
      parts.push({ text: `Edit the first image. The second image is a white-mask indicating the region to modify. ${prompt ?? ERASE}` });
      parts.push({ inline_data: { mime_type: "image/jpeg", data: imageB64 } });
      if (maskB64) parts.push({ inline_data: { mime_type: "image/jpeg", data: maskB64 } });
    } else {
      const text = op === "upscale" ? UPSCALE : (prompt ?? "");
      parts.push({ text });
      parts.push({ inline_data: { mime_type: "image/jpeg", data: imageB64 } });
    }
    const payload = {
      contents: [{ parts }],
      generationConfig: { response_modalities: ["IMAGE"] },
    };
    const resp = await fetch(`${GEMINI_URL}?key=${apiKey}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (resp.status === 429) throw new Error("quota");
    if (!resp.ok) throw new Error(`gemini_http_${resp.status}`);
    const data = await resp.json();
    const part = data?.candidates?.[0]?.content?.parts?.find((p: { inlineData?: { data?: string } }) => p?.inlineData?.data);
    const b64 = part?.inlineData?.data;
    if (!b64) throw new Error("no_image");
    return b64;
  };
}

export function makeOpenRouter(apiKey: string) {
  return async (imageB64: string): Promise<string> => {
    const payload = {
      model: OPENROUTER_MODEL,
      messages: [{
        role: "user",
        content: [
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${imageB64}` } },
          { type: "text", text: "Describe this photo in 2-3 sentences. Be specific about the subjects, setting, and mood." },
        ],
      }],
    };
    const resp = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "authorization": `Bearer ${apiKey}`,
        "http-referer": "com.webstudio.lumagallery",
      },
      body: JSON.stringify(payload),
    });
    if (resp.status === 429) throw new Error("quota");
    if (!resp.ok) throw new Error(`openrouter_http_${resp.status}`);
    const data = await resp.json();
    const content = data?.choices?.[0]?.message?.content;
    if (!content) throw new Error("no_content");
    return String(content).trim();
  };
}
