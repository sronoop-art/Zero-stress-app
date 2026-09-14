# OTA Updates — Remote Config + Asset Packs (no new APK needed)

Your app supports **two kinds of over-the-air updates** without touching the
Play Store (you distribute APKs directly, so this is your update channel):

1. **Config OTA (Firebase Remote Config)** — flip switches and change numbers
2. **Asset OTA (content pack)** — push new PNGs (frames/badges) to every device

Dynamic Feature Delivery (on-demand APK modules) was **not** used: it requires
distribution through Google Play as an AAB, which does not fit direct-APK
distribution.

---

## 1. Config OTA — Firebase Remote Config

Opened from the **Firebase Console → Remote Config**. Values the app reads:

| Key | Default | What it does |
|---|---|---|
| `min_version_code` | `1` | Devices with a lower `versionCode` see a blocking **"Update Required"** dialog at launch |
| `update_url` | *(empty)* | Link the dialog opens (your download page / Drive / direct APK URL) |
| `update_message` | "A new version is available..." | Text in the update dialog |
| `content_pack_url` | *(empty)* | Direct GET URL of the asset ZIP (see section 2) |
| `content_pack_version` | `0` | Bump to force devices to re-download the pack |
| `cloudinary_cloud_name` | *(empty)* | Your Cloudinary cloud name - enables profile-picture uploads |
| `cloudinary_upload_preset` | *(empty)* | UNSIGNED Cloudinary preset (Settings > Upload > Presets) |
| `title_bronze_score` | `600` | Bronze title unlock score |
| `title_silver_score` | `1200` | Silver title unlock score |
| `title_gold_score` | `2000` | Gold title unlock score |
| `title_platinum_score` | `3000` | Platinum title unlock score |
| `title_diamond_score` | `4000` | Diamond title unlock score |
| `title_heroic_score` | `5000` | Heroic title unlock score |
| `title_master_score` | `7000` | Master title unlock score |
| `title_grandmaster_score` | `10000` | Grandmaster title unlock score |

### Forcing an app update

1. Build and upload your new APK somewhere (download page, Drive, etc.)
2. In Remote Config, set `min_version_code` to the **new** `versionCode`
   (bump `versionCode` in `app/build.gradle.kts` with every release!)
3. Set `update_url` to where users get the new APK
4. Publish. Old installs show the blocking update dialog on next launch.

### Tuning title thresholds live

Change e.g. `title_master_score` from 7000 to 5500, publish, and every device
uses the new value the next time it opens the app (fetch runs at splash;
cached values work offline).

---

## 2. Asset OTA — the content pack

A ZIP of PNGs whose **file names match the built-in slots** they override:

```
frame_bronze.png        frame_diamond.png
frame_silver.png        frame_heroic.png
frame_gold.png          frame_master.png
frame_platinum.png      frame_grandmaster.png

ic_title_bronze.png     ic_title_diamond.png
ic_title_silver.png     ic_title_heroic.png
ic_title_gold.png       ic_title_master.png
ic_title_platinum.png   ic_title_grandmaster.png
```

(You don't need every file — only include the art you want to change.)

### How to push new art

1. Zip the PNGs (flat, no folders — names must be exact)
2. Upload the ZIP anywhere with a plain GET URL:
   - Firebase Storage (recommended — you're already set up)
   - a GitHub release asset
   - your own server / CDN
3. In Remote Config set:
   - `content_pack_url` = the direct download URL
   - `content_pack_version` = e.g. `1` (bump for every new pack)
4. Publish. Devices download on the next splash — OTA files **override** the
   bundled drawables, so you can restyle frames/badges any time.

### Rules

- Files land in the app's private storage (`filesDir/ota`) — downloaded art
  is applied on the **next launch** after download.
- Downloaded **frame** files are used first; bundled drawables second; the
  colored-ring fallback last.
- `ic_title_*` badges still read from bundled drawables (swap art by
  including a `frame_*` of the same rank, or update the APK).
- The download is zip-slip safe (only flat `.png` files are extracted) and
  runs silently — failures never block the app.

---

## 3. Quick reference — code entry points

`app/src/main/java/com/zerostress/manager/ota/`

- `ZsRemoteConfig.init()` + `fetchAndActivate()` — splash `onCreate`
- `ZsRemoteConfig.updateRequired()` — version gate check (splash, after load)
- `ZsAssetUpdater.downloadIfNewer()` — background download at splash
- `ZsRankTitles.frameSource()` — picks OTA file → drawable → fallback ring
