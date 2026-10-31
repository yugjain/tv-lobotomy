# Toshiba Android TV with MIC (`songshan`)

- **Model**: Toshiba TV with MIC
- **Board / Codename**: `songshan`
- **Underlying Manufacturer**: Hisense (TVS Regza / MediaTek platform)
- **OS**: Android TV 11 (API Level 30)
- **Firmware Build**: `RTT2.220118.001`
- **Stock Launcher**: `com.google.android.apps.tv.launcherx` (Google TV)

---

## 1. Quick Operations

### Re-enabling Any App
To re-enable any disabled app (e.g. ZEE5, MX Player, or any preloaded app):
```bash
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'pm enable --user 0 <package_name>'
```
Common package names:
- ZEE5: `com.graymatrix.did`
- MX Player TV: `com.mxtech.videoplayer.television`
- BrowseHere Browser: `com.tcl.browser`
- Google Play Games: `com.google.android.play.games`
- Miracast / Screen Mirroring: `com.vt.miracast`

### Changing Dock Apps & Order
The Apple TV dock band pins 4 apps at the bottom.
You have two ways to change them:

#### Method 1: Instant Theme (No Rebuild Needed)
Edit or create a `config.json` inside a theme folder (or use `themes/aurora`):
```json
{
  "dock_apps": [
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "in.startv.hotstar"
  ]
}
```
Push it to the TV:
```bash
TV_HOST=192.168.1.50 tools/theme.sh <path_to_theme_folder>
```

#### Method 2: Rebuilding from Source
Edit `launcher/app/src/main/res/values/config.xml`:
```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.google.android.youtube.tv</item>
    <item>in.startv.hotstar</item>
</string-array>
```
Then run:
```bash
TV_HOST=192.168.1.50 ./examples/toshiba-tv-songshan/build_and_install_tv.sh
```

---

## 2. Changing the Wallpaper

To set a custom background wallpaper:
1. Prepare a 1920x1080 (or 4K) JPEG image named `wallpaper.jpg`.
2. Push it via the theme tool:
   ```bash
   TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py --push wallpaper.jpg /sdcard/Android/data/com.gathin.tvhome/files/wallpaper.jpg
   ```
3. Or place `wallpaper.jpg` inside a theme folder and push with:
   ```bash
   TV_HOST=192.168.1.50 tools/theme.sh <theme_folder>
   ```

---

## 3. AdGuard Private DNS

Ad-blocking Private DNS is active via `dns.adguard-dns.com`. It blocks telemetry, in-app ad banners, and trackers across apps (note: video ads injected directly by YouTube stream servers cannot be blocked by DNS).

### How to Undo Private DNS:
```bash
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'settings put global private_dns_mode opportunistic'
```

---

## 4. Full Factory Restore

To restore the TV to stock Google TV (re-enables all debloated packages, resets the stock Google TV launcher as default Home, and restores Private DNS):
```bash
TV_HOST=192.168.1.50 ./examples/toshiba-tv-songshan/restore_tv.sh
```
Or via the recovery engine:
```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/toshiba-tv-songshan/debloat.txt com.google.android.apps.tv.launcherx
```

---

## 5. Security: Closing Network ADB

When you are satisfied with the setup, close port 5555 so that network ADB is not left exposed:
1. On your TV remote, open **Settings** (or click the Settings pill on the home screen).
2. Go to **Device Preferences** → **Developer options**.
3. Toggle **USB debugging** to **OFF** (and Network/ADB debugging if visible).
4. Select **Revoke USB debugging authorisations** and click OK.
5. Reboot the TV.
6. Verify from your computer terminal that port 5555 is closed:
   ```bash
   nc -vz 192.168.1.50 5555
   ```
   It should report connection refused / closed.
