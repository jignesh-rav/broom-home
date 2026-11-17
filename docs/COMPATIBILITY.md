# Which TVs does Broom Home work on?

Broom is an ordinary Android TV home-screen app:
- plain Java, no native code, so it runs on both 32-bit and 64-bit chips
- needs Android 5.0 or newer; built for Android 9 and later
- doesn't use Google Play services or any Xiaomi APIs

So it isn't tied to Xiaomi. **It runs on any TV or streaming box that runs Android TV or Google TV**, and you set it up the same way: turn on ADB, then run the installer (or ask Claude).

> **Honest status:** it has been tested on **two** TVs so far: a Xiaomi Mi TV and a TCL 4K Android TV (both Android TV 9). The groups below are based on what the code depends on. If you try it on another TV, please open an issue saying what worked, so this list can grow.

## Tested
| Device | Android | Result |
|---|---|---|
| Xiaomi Mi TV 4A / 4C Pro 32" (`dangal`, MiTV-AXSO0), 1 GB | Android TV 9, PatchWall | Everything works |
| TCL 4K Android TV, 2018 range (`BeyondTV`, Realtek RTD285O chip), 2 GB | Android TV 9, stock Android TV home | Everything works, including power-on to a Fire TV over HDMI-CEC. Picture / Sound / Display shortcuts show "Not available" (they're Xiaomi-only); use **All settings**. Debloated with a per-model list made by Claude (see below) |

## Should work: Android TV (untested)
These run the same Android TV system as the tested TV. Everything should work except the Xiaomi-only extras listed further down.
- **Xiaomi / Redmi:** Mi TV 4A, 4C, 4X, 4S, Horizon Edition, 5X, P1, Redmi TV on Android TV 9–11, and Mi Box S / Mi TV Stick
- **Sony** Bravia Android TV (2016–2020)
- **TCL** Android TV models other than the tested one (P / C series 2018 onwards), and **iFFalcon, Thomson, Kodak, Blaupunkt, Nokia, Motorola, Vu, Acer, Toshiba** Android TV models (2018 onwards)
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
| Debloat | Installer Step 2, ready-made list | **TCL:** installer Step 2 with the TCL list. **Other brands:** Step 2 covers the Google packages; for the maker's own bloat, extra steps with Claude: it reads your model and lists its packages, you approve, it disables them in batches and saves a per-model undo list. See [DEBLOAT.md](DEBLOAT.md) |
| Stats page ("before / after") | Mi TV numbers built in | **Extra steps with Claude:** before and after snapshots of *your* TV, then `make_baseline.py`, then rebuild and reinstall the APK (signed with your own key). Optional: without it, the page shows the Mi TV's "before" numbers next to your live ones |
| HDMI device names, power-on to the device that's on | Works when HDMI-CEC is on | Works when HDMI-CEC is on (see [HDMI-CEC](#hdmi-cec-device-names-and-power-on)) |
| Everything else (themes, Briefing, Weather, Panchang, screensavers, pop-ups) | Works | Works |

**The order matters:**
1. Before snapshot
2. Install Broom and make it the default launcher
3. Debloat: installer Step 2, then (other brands) a model-specific list approved in batches
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
| The Xiaomi packages in `scripts/debloat-list.txt` (Xiaomi analytics, Mi TV+, Mi apps) | Not on your TV, so the installer skips them. Its Google packages still apply |
| Stats page "before" numbers (`launcher/assets/baseline.json`) | Show the Mi TV's numbers until Claude rebuilds them for your TV (see above) |
| HDMI-CEC device names on the Inputs row | Works on most TVs; depends on the TV maker's HDMI input service |

## HDMI-CEC: device names and power-on
Broom reads HDMI-CEC through Android's standard TV input service, so it works on any brand whose HDMI inputs report CEC devices (most Android TVs do):
- the Inputs row shows each plugged-in device's name and whether it's on
- at power-on, if a device is on (usually because its remote woke the TV), the TV opens that input instead of Broom

CEC is often **off by default**, and every brand names it differently: *HDMI CEC / HDMI control* (Xiaomi, stock Android TV), *T-Link* (TCL), *BRAVIA Sync* (Sony), *EasyLink* (Philips). It needs to be on both on the TV and on the device (Fire TV: Settings → Display & Sounds → HDMI CEC Device Control). Claude can switch it on over ADB, with your yes. User guide: [FEATURES.md](FEATURES.md#hdmi-devices-and-power-on-hdmi-cec).

## Things learned on the TCL (likely on other brands too)
- **Switched-off packages can come back after a reboot.** On the TCL, `pm disable-user` didn't survive a restart for some system apps that had Play Store updates (Google voice search, the stock home, a few TCL services). For those Claude removes the app for your user instead (`pm uninstall --user 0`). It's still reversible (`cmd package install-existing <package>`), and the factory copy stays on the read-only system partition.
- **The Play Store can put apps straight back.** An app uninstalled while the Play Store is still working through an update batch gets reinstalled a minute later. Uninstall once Play Store → Manage apps shows no pending updates.
- **Factory-installed apps can't always be "reinstalled fresh".** If the Play Store's version of a pre-installed app is signed differently (TCL's Prime Video was), it refuses to install over the factory copy. Keep the factory one or remove it for your user.
- **Weather location on a debloated TV:** with the stock apps gone, nothing else asks Android for the location, so Broom (v14+) asks for a fresh one itself. Location must be on in the TV's settings; otherwise use Weather → Change area.
- **Slow first boots after a clean-up:** after many apps update or are removed, Android recompiles apps at the next boot or two (`dex2oat`). It settles by itself. Turning off Play Store auto-update keeps later boots fast.
- **Wall switch vs standby:** many TVs wake from standby in a few seconds but do a full restart after a power cut. Use the remote's power button.

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
