#!/bin/bash
# undo.sh - put the TV back: turn on everything in debloat-list.txt and every debloat-<brand>.txt,
# restore the stock launcher and screensaver, the TV's own animation and key-repeat settings,
# switch off the DNS ad-blocker and remove Broom.
#   ./undo.sh 192.168.1.50              full undo
#   ./undo.sh 192.168.1.50 --keep-broom turn the debloated packages back on only
TV="${1:?usage: ./undo.sh <tv-ip> [--keep-broom]}"
case "$TV" in *:*) ;; *) TV="$TV:5555";; esac
HERE="$(cd "$(dirname "$0")" && pwd)"
sh_() { adb -s "$TV" shell "$1" 2>/dev/null; }
adb connect "$TV"
echo "Turning the debloated packages back on..."
for f in "$HERE"/debloat-*.txt; do
  [ -f "$f" ] || continue
  sed 's/#.*//' "$f" | tr -d ' \t\r' | while read -r p; do
    [ -n "$p" ] || continue
    sh_ "cmd package install-existing $p" >/dev/null   # brings back a package removed for the user ("remove-for-user")
    sh_ "pm enable $p" >/dev/null
  done
done
# the animation and key-repeat values the installer saved before changing them
restore() { if [ -z "$3" ] || [ "$3" = null ]; then sh_ "settings delete $1 $2" >/dev/null; else sh_ "settings put $1 $2 $3"; fi; }
B="$(sh_ "cat /data/local/tmp/broom-speed-before.txt" | tr -d '\r')"
if [ -n "$B" ]; then
  read -r W T A L <<EOF
$B
EOF
  restore global window_animation_scale "$W"; restore global transition_animation_scale "$T"
  restore global animator_duration_scale "$A"; restore secure long_press_timeout "$L"
  sh_ "rm /data/local/tmp/broom-speed-before.txt"
fi
sh_ "settings delete global private_dns_mode"
sh_ "settings delete global private_dns_specifier"
if [ "${2:-}" != "--keep-broom" ]; then
  sh_ "cmd package install-existing com.google.android.tvlauncher" >/dev/null   # removed for the user on TCL
  sh_ "pm enable com.google.android.tvlauncher" >/dev/null
  sh_ "pm enable com.google.android.apps.tv.launcherx" >/dev/null   # Google TV home
  sh_ "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"
  sh_ "settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop"
  REST="$(sh_ "settings get secure enabled_accessibility_services" | tr -d '\r' | tr ':' '\n' | grep -v com.broom.launcher | grep -v '^null$' | paste -sd: -)"
  if [ -n "$REST" ]; then sh_ "settings put secure enabled_accessibility_services $REST"; else sh_ "settings delete secure enabled_accessibility_services"; fi
  adb -s "$TV" uninstall com.broom.launcher
fi
echo "Done. Reboot the TV to finish:  adb -s $TV reboot"
