# Broom Home

**A fast, ad-free home screen for old Xiaomi Mi TVs, plus a safe debloat.**

Mi TVs from 2018–2021 (Mi TV 4A / 4C / 4X / Horizon, PatchWall on Android TV 9) have only 1 GB of RAM, and the stock home screen fills it with ad rows and telemetry. Broom Home replaces that home screen with something that is light (~0.8 MB APK, ~50 MB RAM, 0% CPU when idle), good-looking and actually useful. A short script switches off the junk packages without touching the remote, HDMI, voice, Chromecast or the Play Store.

Nothing here needs root, a PC app or an account. Every change can be undone.

> Built and tested on a Mi TV 4A/4C Pro 32" (`MiTV-AXSO0`, codename `dangal`, Android TV 9). It should run on most Android TV 9+ boxes and TVs. The remote-button helper is written for Xiaomi PatchWall.

## What you get

**Home screen**
- Big clock, time-of-day greeting (English / हिन्दी / ગુજરાતી), date
- Weather for your area with feels-like, humidity, wind and an air-quality warning; hourly and 7-day strip
- Favourites and Recently used rows, HDMI inputs that show the connected device and whether it's on (HDMI-CEC)
- Ticker strip: live India / IPL cricket score, big-club football score, Nifty, Sensex, S&P, Nasdaq, $/₹, gold
- 15 themes: Auto (dawn / day / dusk / night) and painted scenes (Kerala Backwaters, Desert Fort, Himalayan Peaks, Temple Lamps, Warli Art, Arctic Aurora, Alpine Lake, Canyon Sunset, Film Noir, Neon City, Retro Bollywood…), all drawn in code
- Festival decorations on the day: Diwali, Navratri, Uttarayan, Holi, Christmas, New Year, Independence / Republic Day and more

**Screens**
- **Briefing:** Indian, US and global indices, sector and stock trackers with 5-day charts, an editable watchlist, cricket and football scores, and news (Cricket, Football, Tech, AI, Space, Markets). It can open by itself on the first switch-on of the morning.
- **Weather:** now, warnings, a 3-day walk / jog planner that takes air quality into account, 24 h and 14-day forecasts
- **Panchang:** tithi, nakshatra, yoga, karana, Choghadiya, Rahu Kaal, sunrise / sunset and upcoming festivals, worked out offline for your location
- **Network monitor:** uptime, latency chart, outages, speed test
- **TV stats:** RAM, storage and CPU before vs after

A full tour with the remote is in **[docs/FEATURES.md](docs/FEATURES.md)**.

**Screensavers:** Nebula Drift, Aurora, Warp Speed, Rangoli Mandala, Minimal Clock, Facts & Quotes, Briefing, Panchang, photo packs (Monuments, Nature, Animals, Baby animals, Space, from Wikimedia featured pictures) and your own photos from a USB stick

**Extras**
- Live score pop-ups over any app or HDMI input (a wicket, every 50 runs, goals)
- Market alerts when an index moves 1% or a watchlist stock moves 5%
- Sleep timer, tick / click sounds, night mode for the screensaver
- **Settings → Features** (or **Briefing → Customise**): switch off whole features you don't care about, such as markets, cricket, football, Panchang or any news section. Switched-off features are not downloaded and disappear from home, Briefing, the screensaver and pop-ups.
- If home ever fails to load, a plain "safe mode" app list appears instead of a black screen

All data comes from free public feeds that need no API keys: Open-Meteo, Yahoo Finance (delayed, display only), ESPNcricinfo, ESPN, BBC, NASA, Ars Technica, TechCrunch, MIT Technology Review, Google News and Wikimedia.

## Install: do I need to build anything?

**No.** Most people never build. Pick a path:

| | Path A: ready-made APK (recommended) | Path B: build it yourself |
|---|---|---|
| Who | Anyone who wants Broom on their TV | People who want to change the code |
| You need | A computer on the same Wi-Fi (Windows, Mac or Linux) and ADB | The same, plus Android SDK, JDK 17 and Python 3 |
| Time | About 10 minutes | About 30 minutes the first time |
| Steps | [Path A](#path-a-ready-made-apk-no-build) | [Path B](#path-b-build-from-source) |

### Why a computer (ADB) is needed once

Broom isn't on the Play Store, so it has to be **side-loaded**, which just means installed from an APK file. Copying the APK to the TV on a USB stick will install it, but on its own it's then just another app you open from the app list. **Xiaomi's PatchWall keeps the Home button.**

To make Broom the **real home screen**, these have to be switched on from a computer over ADB:
- Home button → Broom
- Broom opens at power-on
- Broom's screensaver
- Weather location
- Screensaver timers

Mi TVs hide the Accessibility settings page that would normally let you do this on the TV. The installer script does all of it in one go. You do it once per TV, and again only if a Xiaomi update undoes it.

Only Broom is side-loaded. Your other apps (Netflix, YouTube, Hotstar…) stay as they are, and you keep installing apps from the Play Store as usual.

### Path A: ready-made APK (no build)

1. **Turn on ADB on the TV** (one time):
   - Settings → Device Preferences → About → press OK on **Build** 7 times
   - Back → Developer options → **USB debugging: On** (on Mi TVs this also turns on ADB over Wi-Fi)
   - Note the TV's IP address: Settings → Network & Internet → your Wi-Fi, for example `192.168.1.50`

   More help: [docs/SETUP.md](docs/SETUP.md).
2. **Install ADB on your computer**:
   - Windows: `winget install Google.PlatformTools`
   - macOS: `brew install android-platform-tools`
   - Linux: `sudo apt install adb`
3. **Download this repo:** the green **Code** button → **Download ZIP**, then unzip it.
4. **Run the installer** from the `scripts` folder. It downloads the latest `BroomHome.apk` from [Releases](../../releases/latest) by itself:

   Windows: open the `scripts` folder, Shift + right-click on an empty spot → **Open PowerShell window here**, then:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Tv 192.168.1.50
   ```
   macOS / Linux (Terminal, inside `scripts`):
   ```bash
   ./install.sh 192.168.1.50
   ```
5. **On the TV**, accept **"Allow USB debugging?"**. Tick *Always allow from this computer*.
6. Press **Home** on the remote. You're on Broom. If the TV asks which Home app to use, choose **Broom Home → Always**.

Then take the tour in **[docs/FEATURES.md](docs/FEATURES.md)**.

**Optional extras** (add them to the install command):

| Windows | macOS / Linux | What it does |
|---|---|---|
| `-Debloat` | `--debloat` | Disables the packages in [`scripts/debloat-list.txt`](scripts/debloat-list.txt): telemetry, ad rows, and services a TV doesn't need. Edit the list first; each one is explained in [docs/DEBLOAT.md](docs/DEBLOAT.md) |
| `-AdBlockDns` | `--adblock-dns` | AdGuard private DNS: blocks ads and trackers for the whole TV |
| `-Apk path\to.apk` | `APK=path/to.apk ./install.sh …` | Use an APK you already have instead of downloading |

**After a Xiaomi update**, run the same command again. It's safe to repeat and only fixes what's missing.

**Undo everything:** `powershell -ExecutionPolicy Bypass -File .\undo.ps1 -Tv 192.168.1.50` (or `./undo.sh 192.168.1.50`), then reboot the TV.

**No computer at all?** An Android phone with an ADB app can do the same job. See [docs/SETUP.md](docs/SETUP.md#no-computer-use-an-android-phone).

### Path B: build from source

Only needed if you change the code. No Android Studio or Gradle. You need:
- the Android SDK command-line tools with `build-tools` and `platforms;android-28`
- JDK 17 or newer
- Python 3

```bash
cd launcher
./build.sh                                   # -> launcher/build/BroomHome.apk  (Windows: run in Git Bash)
cd ../scripts && ./install.sh 192.168.1.50   # the installer picks up your fresh build automatically
```
The first build creates your own signing key, `launcher/broom.keystore`. Keep it: Android only accepts updates signed with the same key. Moving between the release APK and your own build means uninstalling first, because they're signed with different keys. Details: [docs/SETUP.md](docs/SETUP.md#building).

## Make it yours

The code is plain Java with no libraries, one file per screen. [docs/CUSTOMISE.md](docs/CUSTOMISE.md) shows where to change:
- the default stock lists and news feeds
- greetings and quotes
- themes, festivals and favourites

## Is a newer Android possible on these TVs?

No. The bootloader is locked and there are no custom ROMs. The research is in [docs/OS-UPGRADE.md](docs/OS-UPGRADE.md).

## Safety notes
- Debloating only *disables* packages (`pm disable-user`). Nothing is deleted, and `undo` re-enables everything.
- Never disable `com.droidlogic.*`, Bluetooth, the remote service, `com.android.tv`, Google Assistant, Play services, Play Store, Chromecast or `com.xiaomi.mitv.updateservice`. The list leaves them alone.
- Don't `am force-stop com.broom.launcher`. Android then drops the remote-button helper until you re-run the installer or reboot.
- Market data is delayed and for display only. It is not investment advice.

## License
MIT, see [LICENSE](LICENSE). The fonts in `launcher/assets/fonts` are under the SIL Open Font License (`OFL.txt`).
