#!/usr/bin/env bash
# Grant TV Lobotomy the overlay appop and keep its foreground service out of Doze.
# Run this on the computer that is connected to the TV over ADB.
set -euo pipefail

PACKAGE="com.gathin.tvhome"

adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow
adb shell dumpsys deviceidle whitelist +"$PACKAGE"
