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

**Screensavers:** Nebula Drift, Aurora, Warp Speed, Rangoli Mandala, Minimal Clock, Facts & Quotes, Briefing, Panchang, photo packs (Monuments, Nature, Animals, Baby animals, Space, from Wikimedia featured pictures) and your own photos from a USB stick

**Extras**
- Live score pop-ups over any app or HDMI input (a wicket, every 50 runs, goals)
- Market alerts when an index moves 1% or a watchlist stock moves 5%
- Sleep timer, tick / click sounds, night mode for the screensaver
- **Settings → Features** (or **Briefing → Customise**): switch off whole features you don't care about, such as markets, cricket, football, Panchang or any news section. Switched-off features are not downloaded and disappear from home, Briefing, the screensaver and pop-ups.
- If home ever fails to load, a plain "safe mode" app list appears instead of a black screen

All data comes from free public feeds that need no API keys: Open-Meteo, Yahoo Finance (delayed, display only), ESPNcricinfo, ESPN, BBC, NASA, Ars Technica, TechCrunch, MIT Technology Review, Google News and Wikimedia.

## Quick start (about 10 minutes)

1. **Get the APK.** Download `BroomHome.apk` from the [Releases](../../releases) page, or build it yourself (see [Build from source](#build-from-source)).
2. **Turn on ADB on the TV.** Settings → Device Preferences → About → click **Build** 7 times. Then Developer options → **USB debugging** On (on Mi TVs this also allows network ADB). Note the TV's IP from Settings → Network. Step-by-step: [docs/SETUP.md](docs/SETUP.md).
3. **Install [ADB platform-tools](https://developer.android.com/tools/releases/platform-tools)** on your computer.
4. **Run the installer** from the `scripts` folder:

   Windows (PowerShell):
   ```powershell
   .\install.ps1 -Tv 192.168.1.50 -Apk ..\BroomHome.apk
   ```
   macOS / Linux:
   ```bash
   APK=../BroomHome.apk ./install.sh 192.168.1.50
   ```
   Accept the "Allow debugging" prompt on the TV the first time.
5. Press **Home** on the remote and choose **Broom Home → Always** if asked.

Optional flags:
- `-Debloat` / `--debloat` disables the packages in [`scripts/debloat-list.txt`](scripts/debloat-list.txt) (telemetry, ad rows, camera / print / SMS services a TV doesn't need). Edit the list first; [docs/DEBLOAT.md](docs/DEBLOAT.md) explains each package.
- `-AdBlockDns` / `--adblock-dns` turns on AdGuard private DNS, which blocks ads and trackers for the whole TV.

After a Xiaomi update, run the installer again. It's safe to re-run and puts back anything the update reset.

**Undo everything:** `.\undo.ps1 -Tv 192.168.1.50` (or `./undo.sh 192.168.1.50`), then reboot the TV.

## Build from source

No Android Studio or Gradle needed. You need:
- the Android SDK command-line tools with one `build-tools` and one `platforms;android-28`
- JDK 17+
- Python 3

```bash
cd launcher
./build.sh            # -> build/BroomHome.apk
```
The first build creates your own signing key, `launcher/broom.keystore`. Keep it: Android only accepts updates signed with the same key. It is git-ignored, so it never ends up in the repo. Details: [docs/SETUP.md](docs/SETUP.md#building).

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
