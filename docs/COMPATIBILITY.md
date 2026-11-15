# Which TVs does Broom Home work on?

Broom is an ordinary Android TV home-screen app:
- plain Java, no native code, so it runs on both 32-bit and 64-bit chips
- needs Android 5.0 or newer; built for Android 9 and later
- doesn't use Google Play services or any Xiaomi APIs

So it isn't tied to Xiaomi. **It runs on any TV or streaming box that runs Android TV or Google TV**, and you set it up the same way: turn on ADB, then run the installer (or ask Claude).

> **Honest status:** it has been tested on **one** TV so far: a Xiaomi Mi TV 4A/4C Pro 32" (`MiTV-AXSO0`, Android TV 9, PatchWall). The groups below are based on what the code depends on. If you try it on another TV, please open an issue saying what worked, so this list can grow.

## Tested
| Device | Android | Result |
|---|---|---|
| Xiaomi Mi TV 4A / 4C Pro 32" (`dangal`, MiTV-AXSO0) | Android TV 9, PatchWall | Everything works |

## Should work: Android TV (untested)
These run the same Android TV system as the tested TV. Everything should work except the Xiaomi-only extras listed further down.
- **Xiaomi / Redmi:** Mi TV 4A, 4C, 4X, 4S, Horizon Edition, 5X, P1, Redmi TV on Android TV 9–11, and Mi Box S / Mi TV Stick
- **Sony** Bravia Android TV (2016–2020)
- **TCL, iFFalcon, Thomson, Kodak, Blaupunkt, Nokia, Motorola, Vu, Acer, Toshiba** Android TV models (2018 onwards)
- **OnePlus TV** (Y / U / Q series on Android TV)
- **Philips** Android TV (2016 onwards)
- **Streaming boxes:** Nvidia Shield TV, Mi Box S and other Android TV boxes

Boxes have no HDMI inputs of their own, so the "Inputs" row just stays empty. Set-top boxes from cable / DTH operators are often locked (no Developer options) and may not allow it.

## Should work, with a note: Google TV (untested)
Google TV is Android TV with a different home app (`com.google.android.apps.tv.launcherx`). The installer detects it and switches it off so the Home button opens Broom. `undo` turns it back on.
- **Devices:** Chromecast with Google TV, Google TV Streamer, and Google TV sets from Sony (2021+), TCL, Hisense, Xiaomi (X / A series, 2022+), Philips, Panasonic, etc.
- **What you give up while Broom is home:** Google TV's own home screen, so its recommendation rows and profiles screen. Apps, the Play Store, voice search, Chromecast and the remote keep working.
- **Updates:** a Google TV update can switch its home app back on. Re-run the installer, or ask Claude to.
- **Screensaver:** some Google TV builds use their own "Ambient mode" and may ignore Broom's screensaver. Everything else is unaffected.

## Not supported
These aren't Android TV, so Broom can't be installed as their home screen:
- **Samsung** (Tizen), **LG** (webOS), **Roku TV**, **Apple TV**
- **Amazon Fire TV / Fire Stick** (Fire OS). The APK installs, but Fire OS doesn't let another app take over the Home button.
- **Very old Xiaomi TVs that run PatchWall without Android TV** (some early Mi TV 4 / 4A firmware without the Google Play Store)
- TVs older than Android TV 5.0

## On non-Xiaomi TVs: what's the same and what needs extra steps

| | Xiaomi Mi TV | Any other Android TV / Google TV |
|---|---|---|
| Install Broom and make it the default launcher | Installer, one command | **Same:** installer, one command (Google TV home is switched off automatically) |
| Debloat | `-Debloat` with the ready-made list | **Extra steps with Claude:** it reads your model and lists its packages, you approve, it disables them in batches and saves a per-model undo list. See [DEBLOAT.md](DEBLOAT.md) |
| Stats page ("before / after") | Mi TV numbers built in | **Extra steps with Claude:** before and after snapshots of *your* TV, then `make_baseline.py`, then rebuild and reinstall the APK (signed with your own key). Optional: without it, the page shows the Mi TV's "before" numbers next to your live ones |
| Everything else (themes, Briefing, Weather, Panchang, screensavers, pop-ups) | Works | Works |

**The order matters:**
1. Before snapshot
2. Install Broom and make it the default launcher
3. Debloat, approved in batches
4. After snapshot
5. Rebuild the Stats page

Claude follows this order from [`CLAUDE.md`](../CLAUDE.md#non-xiaomi-tvs-debloat-and-the-stats-page-take-extra-steps). Easiest prompt:

> *"Install Broom Home from https://github.com/jignesh-rav/broom-home on my &lt;brand and model&gt; TV at &lt;IP&gt;, make it the default launcher, then help me debloat it and update the Stats page for my TV. Ask before disabling anything."*

## Xiaomi-only extras
These only do something on Xiaomi PatchWall TVs. Elsewhere they're simply skipped, and nothing breaks.

| Feature | On other TVs |
|---|---|
| Sending PatchWall's own home screens (Mi button, wake-up) back to Broom | Not needed: the installer sets Broom as the Home app |
| Quick settings → Picture & TV options / Sound / Display (Amlogic TV settings shortcuts) | Shows "Not available on this TV". Use **All settings** instead |
| `scripts/debloat-list.txt` (Xiaomi analytics, Mi TV+, Mi apps) | Don't use it; Claude builds a list for your model instead (see above) |
| Stats page "before" numbers (`launcher/assets/baseline.json`) | Show the Mi TV's numbers until Claude rebuilds them for your TV (see above) |
| HDMI-CEC device names on the Inputs row | Works on most TVs; depends on the TV maker's HDMI input service |

## Things that depend on the TV, not on Broom
- **ADB over Wi-Fi:**
  - Android TV 9–10 (most TVs from 2018–2021): turning on USB debugging also opens port 5555, as on the Mi TV.
  - Android TV 11+ and Google TV: you may need **Developer options → Wireless debugging** instead. Then pair first with `adb pair <ip>:<pairing-port>` and the code shown on the TV, then `adb connect <ip>:<port shown>`. Claude can walk you through it.
- **Accessibility service:** pop-ups and the remote-button helper use one. On Android 13+, Settings may show it as "restricted" for side-loaded apps. The installer turns it on over ADB, which is allowed.
- **Low-RAM devices:** Broom uses about 50 MB, so 1 GB TVs are fine. On 2 GB+ devices it simply has more headroom.

## Trying it on a new TV
1. Do README Step 1. On Android 11+ it may be **Wireless debugging**, see above.
2. Run the installer, or ask Claude: *"Install Broom Home from https://github.com/jignesh-rav/broom-home on my <brand/model> TV at <IP>."*
3. Press Home. If something doesn't work, run `undo` and open a GitHub issue with your TV model and Android version.
