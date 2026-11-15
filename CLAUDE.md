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
   - Android TV 11+ / Google TV often needs **Wireless debugging** instead of port 5555. Ask for the pairing IP:port and code shown on the TV, run `adb pair <ip>:<pair-port> <code>`, then `adb connect <ip>:<port>`. Use that `<ip>:<port>` everywhere below in place of `<ip>:5555`, and pass it to the installer as the TV address.
3. **Check the device:** `adb -s <ip>:5555 shell getprop ro.product.model` and `getprop ro.build.version.release`. Broom works on Android TV and Google TV (see `docs/COMPATIBILITY.md`). It has only been tested on a Xiaomi Mi TV with Android TV 9, so say that other models are expected to work but are untested. If the device is Fire OS, Tizen, webOS or Roku, stop and explain that it isn't supported. On Google TV, tell the person first that the installer switches off the Google TV home screen (`undo` restores it).
4. **Run the installer** from `scripts/`. It downloads the latest release APK if there's no local build:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts\install.ps1 -Tv <ip>`
   - macOS / Linux / Git Bash: `bash scripts/install.sh <ip>`
5. **Verify it worked:**
   - `adb -s <ip>:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` should end with `com.broom.launcher/.MainActivity`
   - `adb -s <ip>:5555 shell settings get secure enabled_accessibility_services` should contain `com.broom.launcher/com.broom.launcher.KeyRemapService`
   - Then ask the person to press **Home** on the remote and confirm they see Broom.
6. **Point them to the features:** `docs/FEATURES.md`. Mention **Settings → Features** for switching off markets, scores, Panchang or news.

## Only with the person's explicit yes
- **Debloat** (`-Debloat` / `--debloat`): this list is for **Xiaomi Mi TVs only**. First show them `scripts/debloat-list.txt` and ask which of the optional ones to keep. Comment those lines out, then run. For any other brand, don't use `-Debloat`; follow "Non-Xiaomi TVs" below.
- **Ad-blocking DNS** (`-AdBlockDns` / `--adblock-dns`): it changes DNS for the whole TV.
- **Uninstalling any app.**
- **Rebooting the TV**, because it may interrupt someone watching.

## Non-Xiaomi TVs: debloat and the Stats page take extra steps

`scripts/debloat-list.txt` and `launcher/assets/baseline.json` (the "before / after" numbers on **Settings → TV stats & savings**) were made on a Xiaomi Mi TV. On any other brand, follow this order: install first, then debloat, then rebuild the Stats page.

### 1. Before changing anything, take a "before" snapshot
Do this right after `adb connect`, before running the installer. Save these into `snapshots/<model>-before/`:
```
adb -s <tv> shell getprop                     > getprop.txt
adb -s <tv> shell cat /proc/meminfo           > proc-meminfo.txt
adb -s <tv> shell dumpsys meminfo             > meminfo.txt
adb -s <tv> shell dumpsys cpuinfo             > cpuinfo.txt
adb -s <tv> shell dumpsys diskstats           > diskstats.txt
adb -s <tv> shell pm list packages            > packages-all.txt
adb -s <tv> shell pm list packages -d         > packages-disabled.txt
adb -s <tv> shell pm list packages -s         > packages-system.txt
adb -s <tv> shell pm list packages -3         > packages-thirdparty.txt
```
`snapshots/` is personal data: add it to `.gitignore` and never commit or push it.

### 2. Install Broom and make it the default launcher
Use the normal install steps above, without `-Debloat`, and verify Home goes to Broom.

### 3. Build a debloat list for this model
1. Note the brand, model and Android version from `getprop.txt`: `ro.product.manufacturer`, `ro.product.model`, `ro.build.version.release`.
2. Go through `packages-system.txt` and `packages-thirdparty.txt` and sort each package into one of three groups:
   - **junk:** telemetry, ad / recommendation channels, vendor promos, and apps a TV can't use (camera, print, SMS)
   - **caution:** the vendor home screen, screen mirroring, vendor media apps, the screen reader
   - **keep:** everything in "Never do" below, plus anything for input, HDMI, tuner, picture or sound (vendor `tv`, `hdmi`, `cec`, `tvinput`, `settings`, `factory`, `panel`, `pq` and `audio` packages), remote / Bluetooth, voice, casting, the Play Store, Play services and system updates. If you're unsure what a package does, it goes in keep.
3. **Show the person the junk and caution tables**, with what each package is, and wait for them to pick.
4. Write the approved packages to `scripts/debloat-<brand>-<model>.txt` in the same format as `debloat-list.txt`, then disable each one with `adb -s <tv> shell pm disable-user --user 0 <pkg>`. Disable in small batches. After each batch, ask them to check the remote, HDMI inputs and sound still work. If anything breaks, `pm enable` that batch at once.
5. Undo for this list is `pm enable` for each line. Tell the person to keep the file. `undo.ps1` / `undo.sh` only read `debloat-list.txt`, so point them at the new file instead, or run the `pm enable` lines yourself.
6. Uninstalling instead of disabling is only for apps the person installed themselves, and only with their yes.

### 4. Take an "after" snapshot and rebuild the Stats page
1. Wait a minute or two for the TV to settle on the Broom home screen, then repeat step 1 into `snapshots/<model>-after/`.
2. Create `snapshots/undo.txt` with one `adb shell pm enable <pkg>` line per disabled package. The script counts these lines.
3. Run `python launcher/make_baseline.py snapshots/<model>-before snapshots/<model>-after`. It rewrites `launcher/assets/baseline.json` with this TV's name and its before / after RAM, storage, package count, load and top memory users.
4. To show the new numbers on the TV, rebuild and reinstall. Tell the person first, because the rebuilt APK is signed with their own new key:
   1. `bash launcher/build.sh`
   2. `adb uninstall com.broom.launcher`. This clears Broom's own settings (favourites order, theme, watchlist), which is why it's best done right after the first install.
   3. `adb install launcher/build/BroomHome.apk`
   4. Re-run the installer with `-Apk launcher/build/BroomHome.apk` / `APK=…` to restore permissions and the default launcher.
   5. Tell them to keep `launcher/broom.keystore`. Future updates must be built with it, and the release APK can no longer update their copy.
5. If they don't want to rebuild, skip step 4. Everything else works; the Stats page just keeps showing the Mi TV's "before" numbers next to their TV's live numbers.

## Never do
- Never disable or uninstall the packages that keep the TV working:
  - `com.droidlogic.*`, `com.android.bluetooth`, `com.google.android.tv.remote.service`, `com.android.tv`, `com.android.providers.tv`
  - `com.google.android.katniss`, `com.google.android.gms`, `com.android.vending`, `com.google.android.apps.mediashell`
  - `com.xiaomi.mitv.updateservice`, `com.mitv.tvhome.atv` (PatchWall owns the input-source pop-up)
  - anything that isn't in `scripts/debloat-list.txt` or in a per-model list the person approved (see above)
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
