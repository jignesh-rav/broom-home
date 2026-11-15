# Instructions for Claude: installing Broom Home on someone's TV

People clone this repo and ask Claude to set up Broom Home on their TV. They are usually not developers, so keep each message short and plain. Tell them each time they need to look at the TV.

## What you need from the person
- The TV's IP address, for example `192.168.1.50`.
- Confirmation that README "Step 1" is done: Developer options → USB debugging On.

If either is missing, walk them through README Step 1 and wait for the IP. Don't scan the network to find the TV.

## Install steps
1. **adb:** check with `adb version`. If it's missing, install it:
   - Windows: `winget install Google.PlatformTools`, then open a new shell or use the full path
   - macOS: `brew install android-platform-tools`
   - Linux: `sudo apt install adb`
2. **Connect:** run `adb connect <ip>:5555`, then `adb devices`.
   - `unauthorized`: tell the person to look at the TV and accept "Allow USB debugging?" (tick *Always allow*). Then check again.
   - Time-out or refused: the TV is off or asleep, the IP is wrong, USB debugging is off, or the computer is on a different network. Ask them to check.
3. **Check the device:** `adb -s <ip>:5555 shell getprop ro.product.model` and `getprop ro.build.version.release`. Broom targets Xiaomi Mi TVs on Android TV 9 (PatchWall). On other Android TVs it usually works, but say that the remote-button helper is Xiaomi-specific.
4. **Run the installer** from `scripts/`. It downloads the latest release APK if there's no local build:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts\install.ps1 -Tv <ip>`
   - macOS / Linux / Git Bash: `bash scripts/install.sh <ip>`
5. **Verify it worked:**
   - `adb -s <ip>:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` should end with `com.broom.launcher/.MainActivity`
   - `adb -s <ip>:5555 shell settings get secure enabled_accessibility_services` should contain `com.broom.launcher/com.broom.launcher.KeyRemapService`
   - Then ask the person to press **Home** on the remote and confirm they see Broom.
6. **Point them to the features:** `docs/FEATURES.md`. Mention **Settings → Features** for switching off markets, scores, Panchang or news.

## Only with the person's explicit yes
- **Debloat** (`-Debloat` / `--debloat`): first show them the list in `scripts/debloat-list.txt` and ask which of the optional ones to keep. Comment those lines out, then run.
- **Ad-blocking DNS** (`-AdBlockDns` / `--adblock-dns`): it changes DNS for the whole TV.
- **Uninstalling any app.**
- **Rebooting the TV**, because it may interrupt someone watching.

## Never do
- Never disable or uninstall the packages that keep the TV working:
  - `com.droidlogic.*`, `com.android.bluetooth`, `com.google.android.tv.remote.service`, `com.android.tv`, `com.android.providers.tv`
  - `com.google.android.katniss`, `com.google.android.gms`, `com.android.vending`, `com.google.android.apps.mediashell`
  - `com.xiaomi.mitv.updateservice`, `com.mitv.tvhome.atv` (PatchWall owns the input-source pop-up)
  - anything not in `scripts/debloat-list.txt`
- Never run `am force-stop com.broom.launcher`. Android then drops Broom's accessibility service until a reboot or a re-run of the installer.
- Never send `input keyevent` HOME or POWER while someone may be watching, and never test the sleep timer. It puts the TV into standby, and ADB stays offline until someone presses Power.
- Never flash firmware or try to root the TV. See `docs/OS-UPGRADE.md`.

## Undo
- Windows: `powershell -ExecutionPolicy Bypass -File scripts\undo.ps1 -Tv <ip>`
- macOS / Linux: `bash scripts/undo.sh <ip>`
- Add `-KeepBroom` / `--keep-broom` to only re-enable the debloated packages.
- A reboot finishes the undo. Ask before rebooting.

## Customising and building
To change code (watchlist defaults, feeds, themes…), see `docs/CUSTOMISE.md`.
1. Build with `bash launcher/build.sh`. It needs JDK 17+ (`JAVA_HOME`), the Android SDK and Python 3.
2. Install with `adb install -r launcher/build/BroomHome.apk`.

A self-built APK has a different signature from the release APK. The first time, `adb uninstall com.broom.launcher` is needed, which loses Broom's settings, so ask first. Then re-run the installer to restore permissions.
