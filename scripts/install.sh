#!/bin/bash
# install.sh - put Broom Home on an Android TV / Google TV over Wi-Fi ADB (macOS / Linux / Git Bash). Safe to re-run.
#
#   ./install.sh 192.168.1.50                       install + set up Broom
#   ./install.sh 192.168.1.50 --debloat             also disable the packages in debloat-list.txt
#   ./install.sh 192.168.1.50 --adblock-dns         also turn on AdGuard private DNS
#   APK=path/to/BroomHome.apk ./install.sh 192.168.1.50
set -u
TV="${1:?usage: ./install.sh <tv-ip> [--debloat] [--adblock-dns]}"; shift
case "$TV" in *:*) ;; *) TV="$TV:5555";; esac
DEBLOAT=0; DNS=0
for a in "$@"; do case "$a" in --debloat) DEBLOAT=1;; --adblock-dns) DNS=1;; esac; done
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(dirname "$HERE")"
APK="${APK:-}"
for f in "$ROOT/launcher/build/BroomHome.apk" "$HERE/BroomHome.apk" "$ROOT/BroomHome.apk"; do [ -z "$APK" ] && [ -f "$f" ] && APK="$f"; done
if [ -z "$APK" ]; then
  # No local APK: download the latest release (no build needed)
  APK="$HERE/BroomHome.apk"
  echo "Downloading the latest BroomHome.apk from GitHub Releases..."
  curl -fL -o "$APK" "https://github.com/jignesh-rav/broom-home/releases/latest/download/BroomHome.apk" || rm -f "$APK"
fi
[ -f "$APK" ] || { echo "No APK. Download BroomHome.apk from Releases into scripts/ (or build with launcher/build.sh)."; exit 1; }
command -v adb >/dev/null || { echo "adb not found. macOS: brew install android-platform-tools   Linux: sudo apt install adb"; exit 1; }

PKG=com.broom.launcher
sh_() { adb -s "$TV" shell "$1" 2>/dev/null; }

echo "Connecting to $TV (accept the 'Allow USB debugging' prompt on the TV if it appears)..."
adb connect "$TV"
[ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] || { echo "Could not connect - see docs/SETUP.md"; exit 1; }

echo "Installing $APK ..."
adb -s "$TV" install -r "$APK"

echo "Granting permissions..."
sh_ "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
sh_ "pm grant $PKG android.permission.ACCESS_FINE_LOCATION"
sh_ "pm grant $PKG android.permission.ACCESS_COARSE_LOCATION"
sh_ "pm grant $PKG android.permission.READ_EXTERNAL_STORAGE"
sh_ "appops set $PKG WRITE_SETTINGS allow"
sh_ "appops set $PKG GET_USAGE_STATS allow"

echo "Enabling the remote-button helper..."
SVC="$PKG/$PKG.KeyRemapService"
A11Y="$(sh_ "settings get secure enabled_accessibility_services" | tr -d '\r')"
case "$A11Y" in
  *KeyRemapService*) ;;
  ""|null) sh_ "settings put secure enabled_accessibility_services $SVC"; sh_ "settings put secure accessibility_enabled 1";;
  *) sh_ "settings put secure enabled_accessibility_services $A11Y:$SVC"; sh_ "settings put secure accessibility_enabled 1";;
esac

echo "Setting Broom as the screensaver and the Home app..."
sh_ "settings put secure screensaver_components $PKG/.BroomDream"
sh_ "settings put secure screensaver_enabled 1"
sh_ "cmd package set-home-activity --user 0 $PKG/.MainActivity"
# stock home app off so Home goes to Broom (undo.sh brings it back)
#   Android TV: com.google.android.tvlauncher     Google TV: com.google.android.apps.tv.launcherx
sh_ "pm disable-user --user 0 com.google.android.tvlauncher" >/dev/null
if sh_ "pm list packages" | grep -q "package:com.google.android.apps.tv.launcherx"; then
  echo "Google TV detected: switching off the Google TV home screen (undo.sh turns it back on)."
  sh_ "pm disable-user --user 0 com.google.android.apps.tv.launcherx" >/dev/null
  sh_ "cmd package set-home-activity --user 0 $PKG/.MainActivity"
fi

if [ $DEBLOAT = 1 ]; then
  echo "Disabling packages from debloat-list.txt..."
  INSTALLED="$(sh_ "pm list packages")"
  sed 's/#.*//' "$HERE/debloat-list.txt" | while read -r p; do
    [ -z "$p" ] && continue
    echo "$INSTALLED" | grep -q "package:$p" || continue
    echo "  disable $p"; sh_ "pm disable-user --user 0 $p" >/dev/null
  done
fi
if [ $DNS = 1 ]; then
  sh_ "settings put global private_dns_mode hostname"
  sh_ "settings put global private_dns_specifier dns.adguard-dns.com"
fi
echo
echo "Done. Press Home on the remote. If asked which Home app to use, pick Broom Home -> Always."
echo "Undo any time: ./undo.sh ${TV%:5555}"
