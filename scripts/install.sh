#!/bin/bash
# install.sh - set up Broom Home on an Android TV / Google TV over Wi-Fi ADB (macOS / Linux / Git Bash).
# Safe to re-run (for example after a system update resets something).
#
#   Step 1  install Broom and make it the Home app and screensaver
#   Step 2  debloat: switch off telemetry, ads and unused apps (asks first; undo turns everything back on)
#
#   ./install.sh 192.168.1.50                       install + debloat, asking what to switch off
#   ./install.sh 192.168.1.50 --debloat-preview     only show what the debloat step would switch off, change nothing
#   ./install.sh 192.168.1.50 --no-debloat          install only, leave every other app as it is
#   ./install.sh 192.168.1.50 --debloat             no questions: switch off the safe AND the optional packages
#   ./install.sh 192.168.1.50 --adblock-dns         also turn on AdGuard private DNS
#   APK=path/to/BroomHome.apk ./install.sh 192.168.1.50
#
# Without a terminal to answer questions (e.g. run by a script), only the safe packages are switched off.
set -u
TV="${1:?usage: ./install.sh <tv-ip> [--debloat-preview | --no-debloat | --debloat] [--adblock-dns]}"; shift
case "$TV" in *:*) ;; *) TV="$TV:5555";; esac
DEBLOAT=ask; DNS=0
for a in "$@"; do case "$a" in
  --debloat) DEBLOAT=all;; --no-debloat) DEBLOAT=none;; --debloat-preview) DEBLOAT=preview;; --adblock-dns) DNS=1;;
  *) echo "Unknown option: $a"; exit 1;;
esac; done
INTERACTIVE=0; [ -t 0 ] && [ -t 1 ] && INTERACTIVE=1
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(dirname "$HERE")"
command -v adb >/dev/null || { echo "adb not found. macOS: brew install android-platform-tools   Linux: sudo apt install adb"; exit 1; }

PKG=com.broom.launcher
sh_() { adb -s "$TV" shell "$1" 2>/dev/null; }
ask() {   # ask "question" default(y/n) -> 0 for yes
  local a
  read -r -p "$1 " a </dev/tty || a=""
  a="${a:-$2}"; case "$a" in [yY]*) return 0;; *) return 1;; esac
}

echo "Connecting to $TV (accept the 'Allow USB debugging' prompt on the TV if it appears)..."
adb connect "$TV"
[ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] || { echo "Could not connect - see docs/SETUP.md"; exit 1; }
BRAND="$(sh_ "getprop ro.product.manufacturer" | tr -d '\r' | tr 'A-Z' 'a-z' | tr -cd 'a-z0-9')"
MODEL="$(sh_ "getprop ro.product.model" | tr -d '\r')"
echo "TV: $BRAND $MODEL"

# ---------- debloat plan: what's on this TV from debloat-list.txt (+ debloat-<brand>.txt)
LISTS=("$HERE/debloat-list.txt")
[ -n "$BRAND" ] && [ -f "$HERE/debloat-$BRAND.txt" ] && LISTS+=("$HERE/debloat-$BRAND.txt")
LISTNAMES="debloat-list.txt"; [ ${#LISTS[@]} -gt 1 ] && LISTNAMES="$LISTNAMES + debloat-$BRAND.txt"
plan() {   # prints group|package|mode|description for packages that are on this TV and still on
  local installed disabled seen=" "
  installed="$(sh_ "pm list packages" | tr -d '\r')"
  disabled="$(sh_ "pm list packages -d" | tr -d '\r')"
  for f in "${LISTS[@]}"; do
    awk '/^#[ \t]*---/ { opt = (tolower($0) ~ /optional/); next }
         { line = $0; desc = ""; i = index(line, "#")
           if (i) { desc = substr(line, i + 1); line = substr(line, 1, i - 1) }
           gsub(/[ \t\r]/, "", line); if (line == "") next
           mode = (desc ~ /remove-for-user/) ? "user" : "disable"
           sub(/remove-for-user:?[ \t]*/, "", desc); gsub(/^[ \t]+|[ \t\r]+$/, "", desc)
           print (opt ? "opt" : "safe") "|" line "|" mode "|" desc }' "$f"
  done | while IFS='|' read -r g p m d; do
    case "$seen" in *" $p "*) continue;; esac; seen="$seen$p "
    echo "$installed" | grep -qx "package:$p" || continue
    echo "$disabled" | grep -qx "package:$p" && continue
    echo "$g|$p|$m|$d"
  done
}
show() {   # show <group> <title>
  local n; n="$(echo "$PLAN" | grep -c "^$1|")"
  [ "$n" = 0 ] && return
  echo "  $2 ($n):"
  echo "$PLAN" | grep "^$1|" | while IFS='|' read -r g p m d; do printf '    %-44s %s\n' "$p" "$d"; done
}
off() {   # off <package> <mode>
  if [ "$2" = user ]; then echo "  remove for user  $1"; sh_ "pm uninstall -k --user 0 $1" >/dev/null
  else echo "  switch off       $1"; sh_ "pm disable-user --user 0 $1" >/dev/null; fi
}
PLAN="$(plan)"

if [ $DEBLOAT = preview ]; then
  echo; echo "Debloat preview for this TV (nothing is changed). Lists: $LISTNAMES"
  [ -z "$PLAN" ] && echo "  Nothing to switch off: everything on the lists is already off or not on this TV."
  show safe "Safe: telemetry, ads and apps a TV doesn't use"
  show opt "Optional: your call"
  echo "Never touched: remote, HDMI, picture, sound, voice, Chromecast, Play Store and system updates."
  exit 0
fi

# ---------- Step 1: install Broom and make it Home
echo; echo "== Step 1 of 2: install Broom Home"
APK="${APK:-}"
for f in "$ROOT/launcher/build/BroomHome.apk" "$HERE/BroomHome.apk" "$ROOT/BroomHome.apk"; do [ -z "$APK" ] && [ -f "$f" ] && APK="$f"; done
if [ -z "$APK" ]; then
  # No local APK: download the latest release (no build needed)
  APK="$HERE/BroomHome.apk"
  echo "Downloading the latest BroomHome.apk from GitHub Releases..."
  curl -fL -o "$APK" "https://github.com/jignesh-rav/broom-home/releases/latest/download/BroomHome.apk" || rm -f "$APK"
fi
[ -f "$APK" ] || { echo "No APK. Download BroomHome.apk from Releases into scripts/ (or build with launcher/build.sh)."; exit 1; }

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
if [ "$BRAND" = tcl ]; then
  # TCL switches a disabled stock home back on at every boot: remove it for the user instead (undo restores it)
  sh_ "pm uninstall -k --user 0 com.google.android.tvlauncher" >/dev/null
else
  sh_ "pm disable-user --user 0 com.google.android.tvlauncher" >/dev/null
fi
if sh_ "pm list packages" | grep -q "package:com.google.android.apps.tv.launcherx"; then
  echo "Google TV detected: switching off the Google TV home screen (undo.sh turns it back on)."
  sh_ "pm disable-user --user 0 com.google.android.apps.tv.launcherx" >/dev/null
fi
sh_ "cmd package set-home-activity --user 0 $PKG/.MainActivity"

# ---------- Step 2: debloat
echo; echo "== Step 2 of 2: debloat (switch off what slows the TV down)"
NSAFE="$(echo "$PLAN" | grep -c "^safe|")"; NOPT="$(echo "$PLAN" | grep -c "^opt|")"
DONE=0
if [ $DEBLOAT = none ]; then
  echo "Skipped (--no-debloat). Run the installer again without it any time."
elif [ -z "$PLAN" ]; then
  echo "Nothing to switch off: everything on the lists is already off or not on this TV."
else
  echo "Nothing is deleted: these are only switched off, and undo turns every one back on."
  echo "Never touched: remote, HDMI, picture, sound, voice, Chromecast, Play Store and system updates."
  show safe "Safe: telemetry, ads and apps a TV doesn't use"
  show opt "Optional: your call"
  DO_SAFE=0; DO_OPT=none
  if [ $DEBLOAT = all ]; then DO_SAFE=1; DO_OPT=all
  elif [ $INTERACTIVE = 0 ]; then DO_SAFE=1; echo "(No terminal to ask in: switching off the safe ones only.)"
  else
    [ "$NSAFE" != 0 ] && ask "Switch off the $NSAFE safe ones? [Y/n]" y && DO_SAFE=1
    if [ "$NOPT" != 0 ]; then
      read -r -p "Optional ones: switch off all, none, or choose one by one? [a/N/c] " a </dev/tty || a=""
      case "$a" in [aAyY]*) DO_OPT=all;; [cC]*) DO_OPT=choose;; esac
    fi
  fi
  [ $DO_SAFE = 1 ] && while IFS='|' read -r g p m d; do [ "$g" = safe ] && { off "$p" "$m"; DONE=$((DONE+1)); }; done <<EOF
$PLAN
EOF
  if [ $DO_OPT != none ]; then
    while IFS='|' read -r g p m d; do
      [ "$g" = opt ] || continue
      if [ $DO_OPT = choose ]; then ask "  Switch off $p ($d)? [y/N]" n || continue; fi
      off "$p" "$m"; DONE=$((DONE+1))
    done <<EOF
$PLAN
EOF
  fi
  echo "Switched off $DONE package(s)."
fi

if [ $DNS = 1 ]; then
  sh_ "settings put global private_dns_mode hostname"
  sh_ "settings put global private_dns_specifier dns.adguard-dns.com"
fi
echo
echo "Done. Press Home on the remote. If asked which Home app to use, pick Broom Home -> Always."
if [ $DONE -gt 0 ]; then
  echo "A restart frees the memory those apps were using."
  if [ $INTERACTIVE = 1 ] && ask "Restart the TV now? (it interrupts anyone watching) [y/N]" n; then adb -s "$TV" reboot; echo "Restarting."; fi
fi
echo "Undo any time: ./undo.sh ${TV%:5555}"
