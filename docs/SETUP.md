# Setup guide

## 1. Turn on ADB on the TV

1. On the TV open **Settings → Device Preferences → About**.
2. Scroll to **Build** and press OK on it 7 times. You'll see "You are now a developer".
3. Go back to **Device Preferences → Developer options** and turn **USB debugging** on.
   On most Android TV 9–10 TVs (Mi TVs included) this also opens ADB over the network on port 5555.
   On Android TV 11+ / Google TV, also turn on **Wireless debugging**, then pair once: `adb pair <ip>:<pairing-port>` (enter the code shown on the TV), and connect with `adb connect <ip>:<port shown>`. Pass that `<ip>:<port>` to the installer as `-Tv`.
4. Find the TV's IP address: **Settings → Network & Internet → (your Wi-Fi)**. It looks like `192.168.1.50`.
   Tip: reserve this IP for the TV in your router so it doesn't change.

## 2. Install ADB on your computer

Download **SDK Platform-Tools** from <https://developer.android.com/tools/releases/platform-tools> and unzip it. Then add the folder to your PATH, or run the scripts from inside it.
- Windows: `winget install Google.PlatformTools`
- macOS: `brew install android-platform-tools`
- Linux: `sudo apt install adb`

Check it works:
```bash
adb connect 192.168.1.50:5555
```
The first time, the TV shows **"Allow USB debugging?"**. Tick *Always allow from this computer* and press OK. Then run `adb devices`, which should list the TV as `device`.

## 3. Run the installer

From the repo's `scripts` folder:

| | Windows (PowerShell) | macOS / Linux / Git Bash |
|---|---|---|
| Install Broom | `.\install.ps1 -Tv 192.168.1.50` | `./install.sh 192.168.1.50` |
| Preview the debloat only | `.\install.ps1 -Tv 192.168.1.50 -DebloatPreview` | `./install.sh 192.168.1.50 --debloat-preview` |
| Install without debloat | add `-NoDebloat` | add `--no-debloat` |
| Debloat without questions (safe + optional) | add `-Debloat` | add `--debloat` |
| + ad-blocking DNS | add `-AdBlockDns` | add `--adblock-dns` |
| Use a downloaded APK | add `-Apk ..\BroomHome.apk` | prefix `APK=../BroomHome.apk` |
| Undo all | `.\undo.ps1 -Tv 192.168.1.50` | `./undo.sh 192.168.1.50` |

**Windows:** PowerShell blocks scripts that come from a downloaded ZIP, so start each one like this:
`powershell -ExecutionPolicy Bypass -File .\install.ps1 -Tv 192.168.1.50` (the same for `undo.ps1`).

**macOS / Linux:** if you get "permission denied", run `bash install.sh 192.168.1.50`.

The installer runs two steps. **Step 1** sets up Broom:

| Step | Why |
|---|---|
| Installs the APK | |
| `WRITE_SECURE_SETTINGS` | Lets Broom set itself as the screensaver and keep its remote-button helper on |
| Location | Weather and Panchang for your area. Rounded to about 1 km and only sent to Open-Meteo. You can type an area instead (Weather → Change area) |
| Storage | Screensaver photos from a USB stick |
| `WRITE_SETTINGS`, usage access | Screensaver timer and the "Recently used" row |
| Accessibility service "Broom button helper" | Shows the pop-ups and the sleep timer. On Xiaomi it also sends PatchWall's extra home screens back to Broom; PatchWall stays installed because it owns the input-source pop-up |
| Sets Broom as Home and screensaver, disables the stock Android TV launcher | On TCL the stock launcher is removed for the user instead, because TCL switches it back on at every boot |

**Step 2 debloats the TV.** It reads [`debloat-list.txt`](../scripts/debloat-list.txt) plus the list for your brand (`debloat-tcl.txt` on a TCL), skips packages that aren't on your TV or are already off, and shows the rest:
- **Safe:** telemetry, ad rows and apps a TV can't use. *Switch off the N safe ones? [Y/n]*
- **Optional:** your call. Switch off **a**ll, **n**one (the default) or **c**hoose one by one.

Packages are only switched off (`pm disable-user`), never deleted. The few marked `remove-for-user` are removed for your user (`pm uninstall -k --user 0`), because the TV turns them back on at boot. The remote, HDMI, picture, sound, voice, Chromecast, the Play Store and system updates are never on the lists. At the end the installer offers to restart the TV, which frees the memory. Every package is explained in [DEBLOAT.md](DEBLOAT.md), and `undo` turns all of it back on.

Run by a script or by Claude (no terminal to answer in), Step 2 switches off the safe group only, unless you pass `-NoDebloat` or `-Debloat`.

## No computer? Use an Android phone

Not tested by the author, but it's the same ADB commands run from a phone app instead of a computer.

1. Do step 1 above on the TV: USB debugging on, and note the IP.
2. On an Android phone on the same Wi-Fi, install an ADB client app from the Play Store, for example *Bugjaeger* or *Remote ADB Shell*.
3. Connect to `<TV-IP>:5555` and accept the prompt on the TV.
4. Install the APK. Most ADB apps can install an APK file you've downloaded to the phone: get `BroomHome.apk` from the Releases page first.
5. Open the app's shell and paste this one line:
   ```
   pm grant com.broom.launcher android.permission.WRITE_SECURE_SETTINGS; pm grant com.broom.launcher android.permission.ACCESS_FINE_LOCATION; pm grant com.broom.launcher android.permission.ACCESS_COARSE_LOCATION; pm grant com.broom.launcher android.permission.READ_EXTERNAL_STORAGE; appops set com.broom.launcher WRITE_SETTINGS allow; appops set com.broom.launcher GET_USAGE_STATS allow; settings put secure enabled_accessibility_services com.broom.launcher/com.broom.launcher.KeyRemapService; settings put secure accessibility_enabled 1; settings put secure screensaver_components com.broom.launcher/.BroomDream; cmd package set-home-activity --user 0 com.broom.launcher/.MainActivity; pm disable-user --user 0 com.google.android.tvlauncher
   ```
6. Press **Home** on the remote.

To undo from the phone, run `pm enable com.google.android.tvlauncher; pm uninstall com.broom.launcher` and reboot the TV.

## What works without ADB at all (USB stick only)

You can copy `BroomHome.apk` to a USB stick and install it with the TV's file manager. You'll have to allow "unknown sources" for the file manager when the TV asks. Broom then runs as a normal app from the app list, and the following work:
- Briefing, Weather (use **Change area**), Panchang, themes
- the screensavers, if your TV's Settings has a Screen saver page where you can pick Broom

What you **don't** get without ADB:
- The Home button and power-on still go to the TV's own home screen
- No pop-ups
- No screensaver timers from inside Broom
- An empty "Recently used" row

## 4. First run

- Press **Home**. If the TV asks, pick **Broom Home → Always**.
- **Settings** (top-right button) opens Quick settings: Themes, Screensaver, Weather, Features, Sleep timer, sounds, live score pop-ups and the real TV settings.
- **Settings → Features** switches whole sections on or off: markets, cricket, football, Panchang and each news category.
- Got a Fire TV, set-top box or console on HDMI? Turn **HDMI-CEC** on (TV and device) so its name shows on the Inputs row and switching on from its remote lands on it. See [FEATURES.md](FEATURES.md#hdmi-devices-and-power-on-hdmi-cec).
- **Briefing → Watchlist** edits the stock lists with the remote.
- Long-press OK on an app to add it to or remove it from Favourites, or to move it.

## 5. After a TV system update

Updates sometimes switch the stock launcher back on or reset permissions. Run the installer again. It only fixes what's missing.

## Building

Install:
- **Android SDK command-line tools**: <https://developer.android.com/studio#command-tools>. Then run `sdkmanager "build-tools;34.0.0" "platforms;android-28"`.
- **JDK 17 or newer**, such as Temurin or the JBR bundled with Android Studio / IntelliJ, with `java`, `javac` and `keytool` on PATH.
- **Python 3**.

Then build:
```bash
cd launcher
./build.sh                          # Windows: run it in Git Bash
```
The script looks for the SDK in `ANDROID_HOME`, `ANDROID_SDK_ROOT` or the usual install folders. It uses the newest build-tools and android.jar it finds. Set `ANDROID_JAR=/path/to/platforms/android-28/android.jar` to pick one yourself.

**Signing:** the first build creates `launcher/broom.keystore`. Back it up. A build signed with a different key can't update an installed copy; you'd have to uninstall first and lose Broom's settings. Set `KEYSTORE_PASS` before the first build to choose your own password.

Install a new build quickly:
```bash
adb -s 192.168.1.50:5555 install -r build/BroomHome.apk
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `adb connect` times out | TV off or asleep, wrong IP, or USB debugging off. Some routers block devices from seeing each other ("AP isolation"). |
| `unauthorized` | Accept the prompt on the TV. If it never appeared: Developer options → Revoke USB debugging authorisations, then connect again. |
| Home button still opens the old home screen | Re-run the installer, which re-enables the button helper. Don't `force-stop` Broom. |
| Weather says "set area" or stays on "Finding your location…" | Check Settings → Device Preferences → Location is on (Broom asks the TV for a fresh location). Or Weather → Change area: type an area, a city or `lat, lon`, then pick the right match from the list. |
| Switching on from the Fire TV / set-top box remote doesn't go to its HDMI input | Turn HDMI-CEC on, on the TV and on the device, and keep Quick settings → Power-on on *Broom (or HDMI device that's on)*. The device's name should show on its HDMI tile. See [FEATURES.md](FEATURES.md#hdmi-devices-and-power-on-hdmi-cec). |
| An app you switched off is back after a reboot | Some TVs re-enable updated system apps at boot. Ask Claude to remove it for your user instead (`pm uninstall --user 0 <package>`; undo with `cmd package install-existing <package>`). |
| Slow for a few minutes after boot | Android is recompiling apps after updates (`dex2oat`); it settles by itself. Turn off Play Store → Settings → Auto-update apps so it doesn't recur after every boot, and use standby rather than the wall switch. |
| Photo screensaver stays on the clock | It needs internet the first time. Some networks block `commons.wikimedia.org`; Broom then falls back to Wikipedia's featured pictures. |
| Something went wrong | `undo` puts everything back. Reboot the TV afterwards. |
