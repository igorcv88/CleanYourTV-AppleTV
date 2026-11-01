# Scenic Glass Home — implementation status

Compact context for the next session. Read this, not the old conversation.

Base: `nickoliverio40/apple-tv-launcher` @ `046da15` (this repo's HEAD at start). Architecture unchanged: Java +
Android Views/RecyclerView, no Compose/Kotlin. Package/applicationId unchanged (`com.gathin.tvhome`), so every
installer, recovery script, Home component (`com.gathin.tvhome/.MainActivity`) and test keeps working as before.

## What changed

- **Scenic Home mode** (`config.xml` `scenic_home`, default `true`; theme `"scenic_home"`; on-TV choice in
  Settings → Appearance → Home layout). `ScenicHome.java` is layered over `MainActivity`, which keeps all app
  loading, launching, reorder, inputs, sounds and aerial code. In Scenic mode the grid is the **Apps** screen
  (hidden until All Apps; names under every card), forced to `dock_row=false`, no cinematic shelf.
- **Header:** weather top-left (only when a city is set), clock (Inter Display, tabular digits) and one round
  glass Settings button top-right. The old pill header stays laid out but invisible, so its popups (inputs,
  sounds, aerial picker) still anchor top-right.
- **Dock:** floating glass shelf (48dp side inset, 36dp from bottom, 18dp padding, 16dp gaps, 144×84dp cards,
  scaled down below 960dp width). Horizontal `RecyclerView`, favourites in owner order, **All Apps** as the
  last item. Favourites (`Favorites.java`) are package names in prefs; uninstalled ones stay hidden in place and
  come back on reinstall. Hold OK (or MENU) on a dock card: Move / Remove / App info. Hold OK on an Apps card:
  add to / remove from Dock.
- **Artwork** (`BannerFit.java`, used by both dock and grid): true banners centre-crop only when the crop loses
  ≤10%; other landscape banners are letterboxed on their own edge colour; square/missing banners show the icon at
  native proportions on a brand-coloured card. Never independent X/Y scaling; no in-card name.
- **Focus motion** (`TvFocusMotion`): 1.05×, 4dp lift, 220 ms focus / 180 ms unfocus, ease-out without overshoot,
  1.5° lean, 260 ms highlight (was 1.18×, 280 ms, 10°, 620 ms). Animations retarget from current values.
- **Scenery:** aerial scrims softened in Scenic mode (bottom 0xB3→0x40, top 0x8C→0x4D, shorter). With no aerial
  and no theme wallpaper, `Scenery.java` paints a still night-sky scene in code (not a photo; nothing downloaded).
- **Glass:** `SceneGlassDrawable` paints the screen-space crop of a pre-blurred scene (`Scene`, `SceneCrop`,
  `Blur` box-blur ×3 on a ≤96px copy, done once per scene off the UI thread) under the dock/panels, plus tint and
  the existing `LiquidGlassDrawable` rim. Over moving aerial video: tint + rim only (no live capture).
- **Glass Current** (`NowPlaying`, `MediaAccessService`, `GlassCurrentView`, `MediaTime`): real
  `MediaSessionManager` sessions after notification-listener access; prefers a playing session. Shows art,
  title, artist, only the transport buttons the session's actions allow, and **Listen**. Ribbon: 3.2dp curved
  stroke, line 25dp above the dock top, elapsed left / remaining right (labels outside the stroke). Focusable
  only if `ACTION_SEEK_TO` and a known duration: Left/Right seek 15 s. Updates coalesced (120 ms); art decoded
  and blurred off-thread only when the art key changes; progress ticks once per second only while playing and
  Home is in front. Album blur washes the screen and the glass; the aerial decoder is released under it.
- **Listen:** art grows from the small cover (same centre-crop square) with corner radius 14→28dp on screen,
  420 ms; dock/group step away; ribbon slides into the dock's place. Back reverses and restores prior focus.
- **Settings panel:** right-side glass panel: TV & Chromecast settings (standard `ACTION_SETTINGS`, Xiaomi
  fallback kept), Network, Inputs (only when the TV has any), Home, Apps, Search, Discover, My Space,
  Wallpapers, Appearance, Dock favourites, Sounds, Weather, Music on Home, Reorder apps, Sleep, Backup,
  Advanced launcher settings.
- **Screens:** Search (installed apps + My Space titles, TV keyboard); Discover (only My Space titles and
  apps recently opened from Home — empty state otherwise, no catalogue); My Space (local titles in
  collections, optional app and deep link, hold OK to remove); Wallpapers (aerial / built-in scenery / opt-in
  aerial downloads); Appearance (reduced motion, clear/frosted glass, layout switch); Weather (Open-Meteo city
  lookup, refresh ≤ every 30 min while Home is in front, 5 min retry when offline); Music access (opens the
  system screen, or shows the adb grant command); Backup (where data lives, recovery tools).
- **Installer:** `build-and-install.sh` gained opt-in `MEDIA_ACCESS=1` (grants notification listener). Nothing
  else changed; no device was touched in this task.

## Tests / build (this session, no device)

- JVM tests: `./gradlew testDebugUnitTest` → 153 passed. New: `BannerFitTest`, `FavoritesTest` (incl. DockNav:
  All Apps last, key clamping), `MediaTimeTest` (extrapolation, clamping, unknown duration, seek clamping, action
  flags), `ScenicLogicTest` (Back focus stack, crop model, blur, My Space codec, weather codes, recents).
  `TileFitTest.plainGridFitsFourRowsAtSixColumns`: one assertion relaxed to `<=` because the smaller focus zoom
  makes width-only tiles wider (documented in the test).
- Python `tests/`: 5 failures, identical on the untouched base commit (Hebrew layout/translation coverage) —
  pre-existing, not caused by this work.
- `./gradlew assembleRelease lintDebug`: success. Release APK (R8 minify + shrink) 1,380,660 bytes, **signed
  with the debug key** (no release keystore in the container) — it will not install over a release-signed
  build. Lint: 0 errors, 77 warnings (new-code warnings: notifyDataSetChanged in the dock, English literals).
  Fixed on the way: colour selectors moved to `res/color`; literal BOM characters in three pre-existing aerial
  files replaced by `\uFEFF` escapes (lint `ByteOrderMark` errors).
- No emulator: the container has no KVM, so no screenshots exist. Nothing here proves visual fidelity.

## Known limitations

- New strings are English only (default `values/` and literals in `ScenicHome`); the Hebrew `layout-iw` is not
  adapted (Scenic Home builds its views in code, so it renders, but RTL ordering is untested).
- Apps grid long-press now toggles Dock membership in Scenic mode; App info is reachable from the dock menu only.
- Switching to the classic layout has no way back inside the classic menu (clear data, or Advanced settings
  does not expose it yet).
- Weather needs internet and the Open-Meteo API; failures show nothing (no stale fake reading).
- Discover has no external source: Android TV's `TvContract` preview programs need permissions not requested.
- No benchmark numbers exist; no 60 fps claim is made.
- `build-and-install.ps1` did not get `MEDIA_ACCESS`.

## Device validation still pending (Philips Ambilight, model/firmware unknown)

1. Read-only discovery: `getprop ro.product.model`, `ro.build.version.release`, `ro.build.fingerprint`,
   `ro.board.platform`; `pm list packages -d`; `cmd package resolve-activity -a android.intent.action.MAIN -c
   android.intent.category.HOME`; `dumpsys media_session`.
2. `adb install -r app-release.apk` (fails safely with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` if another build
   signed with a different key is installed — do not uninstall the working Home to "fix" it).
3. `adb shell am start -W -n com.gathin.tvhome/.MainActivity` (cold/warm TotalTime), D-pad across the dock,
   `adb shell dumpsys gfxinfo com.gathin.tvhome` (janky frames), `dumpsys meminfo com.gathin.tvhome`.
4. Check banners of YouTube, Netflix, Disney+, Spotify, Stremio for crop vs letterbox vs icon fallback.
5. Grant media access (`MEDIA_ACCESS=1` or Settings panel), play Spotify: art/title, buttons match the app,
   ribbon times, Left/Right seek, Listen in/out, Back focus.
6. Verify TV & Chromecast settings row resolves on this firmware; Inputs row lists HDMI.
7. Only then run `build-and-install.sh` (it starts the new Home before disabling the stock one and rolls back
   automatically). Never debloat without a per-model audit, a backup and the owner's approval.
