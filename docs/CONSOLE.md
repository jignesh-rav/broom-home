# Broom Console: manage all your TVs from one page

Broom Console is a small page on your computer that checks and controls every TV running Broom Home. It uses the same Wi-Fi ADB connection as the installer, so **nothing new runs or listens on the TVs**.

```bash
python console/broom-console.py
```

On Windows you can also double-click `console\broom-console.cmd`. The page opens in your browser. Leave the terminal window open while you use it; Ctrl+C stops it.

**Needs:** Python 3.7 or newer (standard library only, nothing to `pip install`) and `adb` (see the README, Step 1). It finds `adb` on the PATH, in the Android SDK or in a winget install; otherwise set the `ADB` environment variable to its full path.

## What it does

**Overview:** one card per TV.
- **Connection:** on, screensaver or standby. If ADB can't reach the TV, the card says why in plain words (ADB switched off, the TV asking you to allow this computer, the TV off the network) and what to do.
- **Broom:** version, with an **Update** button when a newer release is out (**Update all TVs** does every TV at once). The release APK is checked against its SHA-256 before it's installed.
- **Health:** memory, storage, uptime, the DNS in use (AdGuard, the Cloudflare backup, or the router's), and 24 h network uptime, latency and the last speed test.
- **Speed:** whether Broom is compiled ahead of time and whether the snappier animation and key-repeat settings are on, with a **Make navigation snappier** button when they aren't (see [PERFORMANCE.md](PERFORMANCE.md#system-level-speed-ups-installer-default)). **Update** compiles Broom again after installing.
- **Buttons:** Screenshot (shown in the page and saved), Test pop-ups, Redraw home, switch the DNS between AdGuard and the router's, Back up settings, and Restart (asks first).

**Settings:** both TVs side by side.
- Covers Features and news sections, languages, pop-up intervals and quiet hours, live-score pop-ups, Still watching?, the screensaver, theme, morning briefing, power-on behaviour and sounds.
- Rows where the TVs differ are shaded. Change any value, or press **Copy** to give the other TVs one TV's value (**Copy every difference** does all rows), then **Save to the TVs**.
- Each TV's weather location, HDMI names, sleep timer and DNS backup state are never copied.

**Favourites:** the home screen's Favourites row on each TV. Reorder, remove, add from that TV's installed apps, or copy one TV's order to the others. Apps a TV doesn't have are struck through, and Broom skips them on that TV.

**Watchlist:** the India and US stock lists, one `SYMBOL, Name` per line, per TV, with **Copy both lists to the other TVs**.

**Backups:** save a TV's Broom settings to this computer (`console/data/backups/<tv>/`) and restore them later. A backup only restores to the TV it came from; use the Settings tab to copy settings between TVs.

## Your TV list

The first time you start the Console, it adds every TV that adb is already connected to (for example the ones you just installed Broom on), named by model. Rename or add TVs at the bottom of the Overview: a name and the TV's IP address. TVs adb is connected to but that aren't in the list yet are offered as one-click suggestions. Give each TV a fixed IP address in your router, so the list stays right.

The page's address includes a key, and the key stays the same each time you start the Console, so you can bookmark it. `--new-key` makes a new one; old bookmarks then stop working.

## Where things are kept

There's no database. Everything is a small file on your computer, and git ignores all of it:

| File | What's in it |
|---|---|
| `console/tvs.json` | The TV list: name and IP address |
| `console/data/key.txt` | The page's key |
| `console/data/backups/<tv>/` | Settings backups, one JSON file each |
| `console/data/shots/` | Screenshots |
| `console/data/apk/` | Release APKs the Console downloaded |

Each TV's settings stay on the TV, inside Broom. The Console doesn't keep a copy: it reads them over ADB when you open a tab and writes back only what you change. Status (memory, network, DNS) is fetched fresh every time.

## Safety
- The page is served to this computer only (`127.0.0.1`), never to your network.
- Every request needs the random key in the page's address, which changes each time you start the Console. Other web pages can't send it commands, and requests for any other host name are refused.
- It only runs a fixed set of actions. There is no box to type ADB commands into, and only the settings listed above can be written, each checked against its allowed values.
- On the TV, Broom's settings receiver only accepts commands from the ADB shell, never from other apps.

## Which Broom version
Everything on the Overview except the Broom details works with any version. The details (memory, network, DNS backup state), **Redraw home**, and the Favourites and Watchlist tabs need **Broom 18.3 or newer**; the Console never sends those commands to an older Broom. Settings and Backups work with any version, but older ones only pick up a change the next time the home screen is rebuilt.

## For developers
- `BROOM_APK=/path/to/BroomHome.apk` (or a build in `launcher/build/`) adds an **Install local build** button.
- `--port 8650` picks another port; `--no-open` only prints the address.
- Broom's side is `BackupReceiver.java`: `BACKUP` and `RESTORE` (settings JSON in `/sdcard/Android/data/com.broom.launcher/files/`; restore merges keys), `STATUS` (writes `broom-status.json`) and `RELOAD` (re-reads watchlists and redraws home if it's on screen). The shared settings and their allowed values are the `SCHEMA` list in `console/broom-console.py`.
