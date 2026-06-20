# WhatsApp Sticker Packs + Sticker Studio — Implementation Plan

**Goal:** Make cutouts add to WhatsApp as *real stickers* (not chat photos), via WhatsApp's third-party Sticker Pack API, plus sticker-making functions: auto-square 512² padding, white outline, text-on-sticker, and a manual pack manager.

**Why the current code fails:** `EditScreen.shareToWhatsApp` uses `ACTION_SEND` (image/png) → WhatsApp always treats that as a chat photo. Real stickers require a ContentProvider + the `ENABLE_STICKER_PACK` intent.

## WhatsApp Sticker Pack hard requirements
- Sticker: **WebP, 512×512, ≤100KB** (static). Tray icon: **96×96, ≤50KB**.
- Pack: **3–30 stickers** (WhatsApp rejects <3). Pack has identifier, name, publisher.
- ContentProvider authority: `${applicationId}.stickercontentprovider`.
- Manifest: `<provider>` (exported) + `<queries>` for `com.whatsapp` and `com.whatsapp.w4b`.
- Add to WhatsApp: `Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")` with extras
  `sticker_pack_id`, `sticker_pack_authority`, `sticker_pack_name`; `startActivityForResult`.

## Storage model
- Packs persisted as JSON in `filesDir/sticker_packs.json`.
- Sticker WebP files in `filesDir/stickers/<packId>/<stickerId>.webp`; tray `tray.webp` per pack.
- `StickerPack(id, name, publisher, stickerFileNames[], trayFileName)`, `Sticker(fileName, emojis[])`.

## Chunks (each its own subagent; verify in Android Studio)
**A. Sticker image processing** (`data/edit/`)
- `BitmapEngine.squarePad512(src)`: center on 512×512 transparent canvas, scale-to-fit.
- `BitmapEngine.whiteOutline(src, strokePx)`: dilate alpha silhouette in white behind subject.
- `BitmapEngine.drawText(src, text, ...)`: draw text (position/size/color) onto bitmap via Canvas.
- `StickerIO.saveWebpSticker(...)` + `saveTrayIcon(...)`: WebP encode (WEBP_LOSSY API30+, WEBP pre-30), enforce size caps (drop quality until ≤100KB / ≤50KB).

**B. Pack storage + repository** (`data/sticker/`)
- `StickerPackRepository`: load/save packs JSON; create/rename/delete pack; add/remove sticker (writes WebP via StickerIO); first sticker becomes tray.
- Models `StickerPack`, `Sticker`.

**C. WhatsApp ContentProvider + manifest + add intent** (`stickers/`)
- `StickerContentProvider` implementing WhatsApp's cursor contract (metadata, stickers, asset files).
- Manifest `<provider>` + `<queries>`.
- `WhatsAppStickerHelper.addPackToWhatsApp(activity, pack)`: builds the intent; guards pack size 3–30.

**D. UI — Sticker Studio + pack manager** (`ui/screens/`)
- After cutout: controls for outline toggle, add-text, then "Add to pack" → pick/create pack (manual picker).
- `StickerPackScreen`: list packs, view/delete stickers, "Add to WhatsApp" (enabled ≥3, else "make N more").
- Nav route + entry point from editor.
- Replace `EditScreen.shareToWhatsApp` image-send with the pack flow.

## Notes
- Cutout source = existing `OnDeviceBgRemover` output (transparent PNG). Pipe through square-pad → optional outline/text → WebP.
- All Android; cannot compile in agent shell (Gradle loopback) — build/verify in Android Studio per chunk.
