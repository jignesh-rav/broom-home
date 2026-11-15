# Debloat: what gets switched off and why

> **This list is for Xiaomi Mi TVs.** Other brands (Sony, TCL, OnePlus, Philips, Google TV…) have their own bloat with different package names. Don't use `-Debloat` there. Instead, ask Claude:
>
> *"Debloat my &lt;brand and model&gt; TV at &lt;IP&gt;. Follow the Non-Xiaomi TVs section of CLAUDE.md."*
>
> Claude will:
> 1. take a before snapshot of the TV
> 2. list every package on your model, sorted into junk / caution / keep
> 3. disable only what you approve, in small batches, checking the remote, HDMI and sound after each batch
> 4. save a per-model list (`scripts/debloat-<brand>-<model>.txt`) so it can be undone
> 5. optionally rebuild the Stats page with your TV's own before / after numbers
>
> Do this **after** Broom is installed and working as the home screen.

`install -Debloat` runs `pm disable-user --user 0 <package>` for every line in [`scripts/debloat-list.txt`](../scripts/debloat-list.txt).
- A disabled package stays on the TV but never starts. You get back its RAM and background CPU.
- `undo` runs `pm enable` on the same list.
- Edit the list before you run the installer. Lines starting with `#` are ignored.

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
- voice: `com.google.android.katniss`, `com.google.android.speech.pumpkin`
- Chromecast: `com.google.android.apps.mediashell`
- core: Play services and the Play Store
- system updates: `com.xiaomi.mitv.updateservice`
- PatchWall `com.mitv.tvhome.atv`, which owns the input-source pop-up and parental lock

Want to remove an app you installed yourself (Facebook, an old streaming app)? Use `adb uninstall <package>`, or Settings → Apps.
