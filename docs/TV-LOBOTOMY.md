# TV Lobotomy: design notes

This document records what was changed, why, and how it was checked. It is the fork's counterpart to
upstream's [`docs/`](.) notes; the debloat lists and tools are upstream and unchanged.

## Base

- Upstream: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), MIT.
- Base commit: `c524b27c` ("README: Product Hunt badge, light and dark", 20 Oct 2026) — the
  `versionCode 4` / `versionName 2.3` build that was installed on the reference TV.
- Reference TV: Toshiba TV with MIC (`Toshiba_TV_with_MIC`, board `songshan`, Hisense/MediaTek),
  Android TV 11, stock launcher `com.google.android.apps.tv.launcherx`.
- Package id stays `com.gathin.tvhome`. Nothing is renamed, so a new build installs in place over the
  one already on the TV and Home keeps working.

## What it looks like

CleanFrame's layout, rebuilt as a native launcher rather than a web app:

- A single vertical list of apps down the left, with large type.
- Monochrome vector icons and the app name below each one; no banner cards, no tile backgrounds.
- Clock and date top right; Inputs / All apps / Settings bottom right, in a quiet text style.
- Soft corner scrims (`ListScrimView`) so the wallpaper stays legible behind white text.
- Ambient colour glow and the hero name are off by default in this theme, so the wallpaper does not
  shift colour as focus moves.

## What changed from upstream

| File | Change | Added lines |
| --- | --- | --- |
| `MainActivity.java` | List layout branch: places the app list, clock and utilities, suppresses the hero name and top shelf, wires focus and key handling for the list | ~251 |
| `AppAdapter.java` | `item_app_list` binding, monochrome icon mapping, transparent tiles, focus alpha/scale | ~188 |
| `Config.java` | Reads `layout`, `cleanframe_dock` and `ambient_glow` from `config.xml` and the JSON overlay | ~24 |
| `DockDecoration.java` | Cleanframe dock pill: frosted band and rim instead of the opaque card | ~5 |
| `ListScrimView.java` | New view: corner radial scrims, ARGB_8888 bitmap, drawn behind the list | 139 (new) |
| `res/layout/item_app_list.xml` | New list row: icon, label, focus background | 57 (new) |
| `res/layout/activity_main.xml` | List container, clock/date block, utility row | — |
| `res/values/config.xml` | `ambient_glow`, `cleanframe_dock`, `list_layout` defaults (`false`) | — |
| `res/drawable/ic_clean_*.xml` | 12 white vector brand glyphs (Netflix, Prime Video, YouTube, JioHotstar, Disney+, Apple TV, Spotify, SonyLIV, Stremio, Play Store, Media Center, Settings) | — |
| `res/drawable/list_item_card_bg.xml`, `quiet_util_*.xml` | Focus and text styles for the list and utilities | — |
| `themes/cleanframe/config.json` | `layout: "list"`, `cleanframe_dock: true`, the ten-app dock, `ambient_glow: false` | — |
| `examples/toshiba-tv-songshan/` | This TV's debloat list, notes and build/restore scripts | — |
| `tools/set_wallpaper.sh` | Wallpaper helper: takes an image path instead of a hardcoded folder | — |

Everything else — the 80 example lists, `tools/tv.py`, `recovery.py`, the docs, the CI workflow — is
upstream as of the base commit.

## How the switch works

`layout: "list"` in a theme's `config.json` is overlaid onto `config.xml` by `Config`, which exposes
`isListLayout`. `MainActivity` reads it once at startup and inflates/binds the list path instead of
the grid path; `cleanframe_dock` controls the monochrome dock treatment and hides the hero name. Both
are ordinary config keys, so reverting is `tools/theme.sh --reset` (or `layout` removed) — no rebuild,
no reinstall.

Icons are matched by package name in `AppAdapter.getCleanIcon`; anything not in the map falls back to
the app's own icon, colour-filtered white, so a new app looks deliberate without a code change.

The wallpaper is deliberately **not** committed: the CleanFrame photographs are third-party work.
`tools/set_wallpaper.sh` crops a user-supplied 1920×1080 image and pushes it with the theme. Because
`theme.sh` removes the TV's wallpaper when the theme folder has none, the README tells you to add an
image first.

## Verification

Checked read-only over ADB, then by rebuilding:

- At verification time the APK installed on the TV was byte-identical to the release artifact this
  tree produced (SHA-256 `cd228637edbba4a6129ab7d9dd95bb2ee55527fecff629510f3d1236c9a9c64b`), and the
  TV's package manager reported `versionCode=4`, `versionName=2.3`, with a last-update time matching
  the build. Since then the only launcher change is mirroring the list-layout views into the Hebrew
  layout, so upstream's "every default view exists in the Hebrew layout" test keeps passing. It is
  inert outside Hebrew and the installed build is unchanged. Rebuilds are not byte-reproducible (zip
  timestamps), so compare size and behaviour rather than hashes; the current build is 351,285 bytes.
- Home resolves to `com.gathin.tvhome/.MainActivity` and the launcher is the default HOME activity.
- The live theme on the TV is `themes/cleanframe/config.json` verbatim (`layout: "list"`,
  `cleanframe_dock: true`), with a `theme_revision` marker from a `tools/theme.sh` push.
- Screenshot: [`docs/tv-lobotomy.jpg`](tv-lobotomy.jpg), captured from the TV with `screencap`.

No device identifiers from that session (serial, Android ID, Wi-Fi MAC, network address, device name)
are in this repository, and the local backup folder they came from is excluded by `.gitignore`.
`backup*/`, `launcher/.tools/`, build outputs, `local.properties`, the Python venv and ADB keys are
all ignored.

## Limitations

- The list layout has been run on one TV. Upstream supports hundreds of Android TV / Google TV models,
  but the list is untested elsewhere; expect to adjust spacing and the dock for other screen sizes.
- The release APK is signed with the debug key, as upstream does for sideloading. There is no
  keystore, and there is no Play Store release.
- The 12 vector icons cover the common streaming apps; everything else uses the app's own icon.
- Fire TV cannot hand Home to a third-party launcher, and Samsung/LG run different operating systems.
- The debloat list is specific to this TV's firmware. Read it before running it, and use
  `examples/toshiba-tv-songshan/restore_tv.sh` (or `tools/restore.sh`) to undo it.

## Rebuilding

```bash
cd launcher
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

JDK 17 and Android SDK 34 are required. Upstream's CI also enforces `release APK < 400 KB`; the
current build is ~350 KB. `./build-and-install.sh` with `TV_HOST` set does the build, install and
Home handover in one step.
