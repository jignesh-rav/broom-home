# Debloat: what gets switched off and why

Debloat is **Step 2 of the installer**. After Broom is set up as the Home app, the installer:
1. reads [`scripts/debloat-list.txt`](../scripts/debloat-list.txt) and, if there is one, the list for your TV's brand (`scripts/debloat-<brand>.txt`, for example [`debloat-tcl.txt`](../scripts/debloat-tcl.txt))
2. skips every package that isn't on your TV or is already off
3. shows the rest in two groups and asks:
   - **Safe** (telemetry, ads, apps a TV can't use): yes by default
   - **Optional** (your call): no by default; you can also choose one by one
4. switches off what you said yes to, then offers to restart the TV

Preview it first, changing nothing: `.\install.ps1 -Tv <ip> -DebloatPreview` or `./install.sh <ip> --debloat-preview`. Skip it with `-NoDebloat` / `--no-debloat`.

| TV | Lists used |
|---|---|
| Xiaomi Mi TV (PatchWall) | `debloat-list.txt`, tested on a 1 GB Mi TV 4A Pro |
| TCL | `debloat-list.txt` + `debloat-tcl.txt`, tested on a 2018 TCL 4K (BeyondTV) |
| Any other Android TV / Google TV | `debloat-list.txt`: only its Google packages are found (feedback, bug reports, recommendation rows, screensaver, Play Movies…) |

**Other brands** (Sony, Philips, OnePlus, Google TV…) have their own bloat with different package names. For a model-specific clean-up, ask Claude after the install:

> *"Debloat my &lt;brand and model&gt; TV at &lt;IP&gt; further. Follow the Non-Xiaomi TVs section of CLAUDE.md."*

Claude snapshots the TV, lists every package sorted into junk / caution / keep, disables only what you approve in small batches (checking the remote, HDMI and sound after each), and saves a per-model list (`scripts/debloat-<brand>-<model>.txt`) that `undo` also reads.

### How a package is switched off
- `pm disable-user --user 0 <package>`: the package stays on the TV but never starts. You get back its RAM and background CPU.
- Lines marked `remove-for-user` (TCL apps that switch themselves back on at every boot): `pm uninstall -k --user 0 <package>`. The app stays on the TV, just not for your user.
- `undo` runs `cmd package install-existing` and `pm enable` for every package in every `debloat-*.txt`.
- Edit the lists before you run the installer: delete a line, or put `#` in front of it, to never have it switched off. Lines under a `# --- optional` heading are asked about separately.

Measured on a 1 GB Mi TV 4A Pro (idle, home screen):

| | Before | After |
|---|---|---|
| Free RAM | ~180 MB | 280–400 MB |
| Free storage | 1.48 GB | 1.70 GB |
| Active packages | 119 | 89 |

## Telemetry and ads (safe)
| Package | What it is |
|---|---|
| com.miui.tv.analytics | Xiaomi analytics |
| com.xiaomi.statistic | Xiaomi statistics / telemetry |
| com.google.android.feedback | Google feedback reporter |
| com.google.android.tv.bugreportsender | Bug report uploader |
| com.mitv.tvhome.mitvplus | Mi TV+ ad / content channels |
| com.google.android.tvrecommendations | Recommendation / ad rows on the stock home |
| com.google.android.leanbacklauncher.recommendations | Old recommendations service |
| com.xm.webcontent | Xiaomi web promos |

## Unused on a TV (safe)
| Package | What it is |
|---|---|
| com.xiaomi.mitv.handbook | User manual |
| com.xiaomi.android.TV.audio | Factory audio test tool |
| com.android.camera2 | Camera (no camera) |
| com.android.gallery3d | AOSP gallery |
| com.android.printspooler | Printing |
| com.android.smspush | SMS / WAP push (no SIM) |
| com.google.android.syncadapters.calendar | Calendar sync |

## Optional: your call
| Package | Disable if… |
|---|---|
| com.google.android.backdrop | you use Broom's screensavers (frees ~180 MB of storage) |
| com.google.android.youtube.tvkids | nobody uses YouTube Kids |
| com.xiaomi.mimusic2, com.mitv.gallery | you don't play music or photos from USB with the Mi apps |
| com.google.android.marvin.talkback | nobody needs the screen reader |
| com.mitv.milinkservice, com.xiaomi.mitv.smartshare, com.xiaomi.wfdsinkhelperservice | you don't use Mi / Miracast screen mirroring (Chromecast keeps working) |
| com.google.android.videos, com.google.android.play.games | you don't use Play Movies / Play Games |

## Never disable
These keep the TV working:
- remote and Bluetooth: `com.android.bluetooth`, `com.droidlogic.ble`, `com.google.android.tv.remote.service`, `com.android.inputdevices`
- HDMI, picture, sound and tuner: everything `com.droidlogic.*`, `mitv.service`, `com.xiaomi.mitv.tvmanager`, `com.android.tv`, `com.android.providers.tv`
- voice: `com.google.android.katniss`, `com.google.android.speech.pumpkin` (on a TV whose remote has no microphone, voice search can go, but only if you ask: it uses ~200 MB of RAM, and the Google TV phone app's voice search stops too)
- Chromecast: `com.google.android.apps.mediashell`
- core: Play services and the Play Store
- system updates: `com.xiaomi.mitv.updateservice`
- PatchWall `com.mitv.tvhome.atv`, which owns the input-source pop-up and parental lock

Want to remove an app you installed yourself (Facebook, an old streaming app)? Use `adb uninstall <package>`, or Settings → Apps.
