# Daily-message overlay test checklist

Prerequisites:

1. Install the build on the TV.
2. Run `./setup_overlay.sh` once, then open Home again so the foreground service starts.
3. Confirm the message is drawn once, not twice. With the appop granted, the launcher hides its own copy and only `OverlayService` draws it.

For each app below, open it from Home and check all three columns:

| App | Message visible | Remote still works (D-pad / OK / Back) | Focus not stolen |
| --- | --- | --- | --- |
| Netflix | | | |
| YouTube | | | |
| Prime Video | | | |
| JioHotstar | | | |
| Apple TV | | | |
| One HDMI input | | | |
| System Settings | | | |
| A video with subtitles | | | |

What "focus not stolen" means: the app's own focus highlight/cursor behaves exactly as before; the message is decoration and must never receive D-pad focus. Because the overlay uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`, OK/Back/arrows should pass through.

Also check:

- [ ] The message never overlaps the Apple TV row or the Settings label on Home.
- [ ] The message sits inside the safe area, 4% of screen height above the bottom edge.
- [ ] Turn the overlay permission off (`adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW ignore`), reopen Home, and confirm the launcher's own copy appears and there is still no double text.
- [ ] Grant it again and confirm the launcher copy disappears.
- [ ] Wait 5 minutes and take two screenshots; the text should have shifted by no more than 2px in any direction.
- [ ] On a subtitled video, the subtitle is not covered and the remote can still seek/pause.
