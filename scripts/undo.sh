#!/bin/bash
# undo.sh - re-enable everything in debloat-list.txt, restore the stock launcher/screensaver, remove Broom.
#   ./undo.sh 192.168.1.50              full undo
#   ./undo.sh 192.168.1.50 --keep-broom re-enable packages only
TV="${1:?usage: ./undo.sh <tv-ip> [--keep-broom]}"
case "$TV" in *:*) ;; *) TV="$TV:5555";; esac
HERE="$(cd "$(dirname "$0")" && pwd)"
sh_() { adb -s "$TV" shell "$1" 2>/dev/null; }
adb connect "$TV"
sed 's/#.*//' "$HERE/debloat-list.txt" | while read -r p; do [ -n "$p" ] && sh_ "pm enable $p" >/dev/null; done
sh_ "settings delete global private_dns_mode"
sh_ "settings delete global private_dns_specifier"
if [ "${2:-}" != "--keep-broom" ]; then
  sh_ "pm enable com.google.android.tvlauncher" >/dev/null
  sh_ "pm enable com.google.android.apps.tv.launcherx" >/dev/null   # Google TV home
  sh_ "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"
  sh_ "settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop"
  REST="$(sh_ "settings get secure enabled_accessibility_services" | tr -d '\r' | tr ':' '\n' | grep -v com.broom.launcher | grep -v '^null$' | paste -sd: -)"
  if [ -n "$REST" ]; then sh_ "settings put secure enabled_accessibility_services $REST"; else sh_ "settings delete secure enabled_accessibility_services"; fi
  adb -s "$TV" uninstall com.broom.launcher
fi
echo "Done. Reboot the TV to finish:  adb -s $TV reboot"
