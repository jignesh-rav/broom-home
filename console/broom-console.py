#!/usr/bin/env python3
"""
Broom Console: one page on this computer that checks and controls every TV running Broom Home.

    python console/broom-console.py             # opens the page in your browser
    python console/broom-console.py --no-open   # only print the address
    python console/broom-console.py --port 8650

It talks to the TVs over ADB (the same Wi-Fi ADB the installer uses), so nothing new runs or
listens on the TVs. The page is served to this computer only (127.0.0.1), and every request
needs the random key in the page's address, so other devices and other web pages can't use it.
It only runs fixed actions: there is no way to type an ADB command into it.

No database: everything is a small file, all kept out of git.
  console/tvs.json        the TV list (filled from the TVs adb already knows on the first run; edit it in the page)
  console/data/key.txt    the page's key, so a bookmarked address keeps working (--new-key makes a new one)
  console/data/backups/   settings backups, one JSON file per backup
  console/data/shots/     screenshots;  console/data/apk/  downloaded release APKs
Each TV's own settings stay on the TV, in Broom; the Console reads and writes them over ADB when asked.
Python 3.7+ standard library only.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CONF = os.path.join(HERE, "tvs.json")
DATA = os.path.join(HERE, "data")
UI = os.path.join(HERE, "ui.html")
REPO = "jignesh-rav/broom-home"
PKG = "com.broom.launcher"
RECV = PKG + "/.BackupReceiver"
EXT = "/sdcard/Android/data/" + PKG + "/files"
LOCAL_APK = os.environ.get("BROOM_APK") or os.path.join(ROOT, "launcher", "build", "BroomHome.apk")   # "Install local build"
NEEDS_STATUS = (18, 3)          # STATUS / RELOAD exist from Broom 18.3 (older ones must never get them)
NO_WINDOW = 0x08000000 if os.name == "nt" else 0

ADB = None
KEY = ""                        # set in main(): kept in console/data/key.txt
PORT = 8642

# --------------------------------------------------------------------------- settings Broom shares
# Only these keys are read, compared and written. Everything else on a TV (weather location, HDMI
# names, sleep timer, DNS backup state, caches, timestamps) stays on that TV.
LANGS = [["en", "English"], ["hi", "Hindi"], ["gu", "Gujarati"], ["mr", "Marathi"]]
EVERY = [[0, "Off"], [10, "Every 10 min"], [15, "Every 15 min"], [30, "Every 30 min"], [60, "Every hour"]]
THEMES = [["auto", "Auto (time of day)"], ["dawn", "Dawn"], ["day", "Day"], ["dusk", "Dusk"], ["night", "Night"],
          ["kerala", "Kerala Backwaters"], ["rajasthan", "Desert Fort"], ["himalaya", "Himalayan Peaks"],
          ["temple", "Temple Lamps"], ["warli", "Warli Art"], ["arctic", "Arctic Aurora"], ["alpine", "Alpine Lake"],
          ["canyon", "Canyon Sunset"], ["noir", "Film Noir"], ["neon", "Neon City"], ["bollywood", "Retro Bollywood"]]
DREAMS = [["nebula", "Nebula Drift"], ["aurora", "Aurora"], ["warp", "Warp Speed"], ["mandala", "Rangoli Mandala"],
          ["clock", "Minimal Clock"], ["quotes", "Facts & Quotes"], ["desi", "Desi Clock"], ["sayings", "Sayings"],
          ["briefing", "Briefing"], ["panchang", "Panchang"], ["photo:monuments", "Photos: Monuments"],
          ["photo:nature", "Photos: Nature"], ["photo:animals", "Photos: Animals"], ["photo:baby", "Photos: Baby animals"],
          ["photo:space", "Photos: Space"], ["photo:usb", "My photos (USB)"], ["shuffle", "Shuffle"]]
SCHEMA = [
    ["Features", [
        ["f_markets", "b", True, "Markets: indices, stocks, home ticker, alerts"],
        ["f_cricket", "b", True, "Cricket live scores"],
        ["f_football", "b", True, "Football scores"],
        ["f_panchang", "b", True, "Panchang: home line, button, screensaver"],
        ["n_cricket", "b", True, "News: Cricket"], ["n_football", "b", True, "News: Football"],
        ["n_tech", "b", True, "News: Tech"], ["n_ai", "b", True, "News: AI"],
        ["n_space", "b", True, "News: Space"], ["n_markets", "b", True, "News: Market news"]]],
    ["Languages", [
        ["lang_panchang", "s", "en", "Panchang (calendar) language", LANGS],
        ["lang_dream_en", "b", True, "Screensaver rotates through: English"],
        ["lang_dream_hi", "b", True, "Screensaver rotates through: Hindi"],
        ["lang_dream_gu", "b", True, "Screensaver rotates through: Gujarati"],
        ["lang_dream_mr", "b", True, "Screensaver rotates through: Marathi"],
        ["lang_news_hi", "b", True, "Hindi news"], ["lang_news_gu", "b", True, "Gujarati news"],
        ["lang_news_mr", "b", True, "Marathi news"]]],
    ["Pop-ups", [
        ["pulse_market", "i", 10, "Market pulse", EVERY],
        ["pulse_cricket", "i", 10, "Live cricket score", EVERY],
        ["pulse_weather", "i", 10, "Weather heads-ups", EVERY],
        ["pulse_news", "i", 10, "Top headline", EVERY],
        ["pulse_panchang", "i", 0, "Panchang moments", [[0, "Off"], [1, "On"]]],
        ["pulse_wellness", "i", 0, "Break reminder", [[0, "Off"], [60, "After 1 hour"], [90, "After 1½ hours"], [120, "After 2 hours"]]],
        ["pulse_quiet", "b", True, "Quiet hours 11 PM – 7 AM"],
        ["score_popups", "b", True, "Live score pop-ups (wickets, goals)"]]],
    ["Still watching?", [
        ["sw_on", "b", True, "Ask before standby when nobody presses a key"],
        ["sw_hours", "i", 3, "Ask after", [[1, "1 hour"], [2, "2 hours"], [3, "3 hours"], [4, "4 hours"], [6, "6 hours"]]],
        ["sw_count", "i", 180, "Countdown", [[60, "1 min"], [120, "2 min"], [180, "3 min"], [300, "5 min"]]],
        ["sw_hdmi", "b", False, "Also on HDMI inputs"]]],
    ["Screensaver", [
        ["dream_mode", "s", "nebula", "Style", DREAMS],
        ["brief_in_screensaver", "b", True, "Briefing inside every screensaver"],
        ["night_saver", "b", True, "Night power saving (midnight – 6 AM)"]]],
    ["Home", [
        ["theme", "s", "auto", "Theme", THEMES],
        ["morning_brief", "b", True, "Morning briefing at first power-on"],
        ["power_on_home", "b", True, "Power-on opens Broom (else the last input)"],
        ["power_on_device", "b", True, "Power-on switches to an HDMI device that's on"],
        ["sounds", "b", True, "Broom sounds"]]],
]
KEYS = {k[0]: k for _, rows in SCHEMA for k in rows}
PKG_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
SYM_RE = re.compile(r"^[A-Za-z0-9.^=&\-]{1,20}$")


class Fail(Exception):
    pass


# --------------------------------------------------------------------------- adb
def find_adb():
    env = os.environ.get("ADB")
    if env and os.path.isfile(env):
        return env
    p = shutil.which("adb")
    if p:
        return p
    home, la = os.path.expanduser("~"), os.environ.get("LOCALAPPDATA", "")
    for pat in [os.path.join(la, "Android", "Sdk", "platform-tools", "adb.exe"),
                os.path.join(la, "Microsoft", "WinGet", "Packages", "Google.PlatformTools*", "platform-tools", "adb.exe"),
                os.path.join(la, "Microsoft", "WinGet", "Links", "adb.exe"),
                os.path.join(home, "Library", "Android", "sdk", "platform-tools", "adb"),
                os.path.join(home, "Android", "Sdk", "platform-tools", "adb"),
                "/opt/homebrew/bin/adb", "/usr/local/bin/adb", "/usr/bin/adb"]:
        for m in glob.glob(pat):
            return m
    return None


_locks, _guard = {}, threading.Lock()


def lock_for(serial):
    with _guard:
        return _locks.setdefault(serial, threading.Lock())


def adb(args, serial=None, timeout=20, binary=False):
    if not ADB:
        raise Fail(HINTS["noadb"])
    cmd = [ADB] + (["-s", serial] if serial else []) + args
    try:
        r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, creationflags=NO_WINDOW)
    except subprocess.TimeoutExpired:
        return 124, (b"" if binary else ""), "timed out"
    out = r.stdout if binary else r.stdout.decode("utf-8", "replace").replace("\r\n", "\n")
    return r.returncode, out, r.stderr.decode("utf-8", "replace")


def sh(serial, command, timeout=25):
    return adb(["shell", command], serial, timeout)[1].strip()


HINTS = {
    "off": "ADB isn't answering, though the TV is on the network. Turn it back on: Settings → Device preferences → "
           "Developer options → USB debugging. On a Xiaomi it can switch itself off after a restart.",
    "unreachable": "The TV isn't on the network: it's switched off at the wall, in deep standby, or its IP address "
                   "changed (check the router, and give the TV a fixed address there).",
    "unauthorized": "The TV is asking to trust this computer. On the TV, accept \"Allow USB debugging?\" and tick "
                    "Always allow from this computer.",
    "offline": "ADB can see the TV but it isn't answering yet. Wait a few seconds; if it stays like this, switch the TV off and on.",
    "noadb": "adb wasn't found on this computer. Install Android platform-tools, or set the ADB environment variable to adb's full path.",
}


def connect(serial):
    """None when ready, else a HINTS key."""
    _, out, err = adb(["connect", serial], timeout=12)
    text = (out + " " + err).lower()
    if "connected to" not in text:
        if "authenticat" in text or "unauthorized" in text:
            return "unauthorized"
        return "off" if ("refused" in text or "10061" in text) else "unreachable"
    for attempt in range(2):
        _, st, err = adb(["get-state"], serial, timeout=8)
        st = (st + " " + err).lower()
        if st.strip().startswith("device"):
            return None
        if "unauthorized" in st:
            return "unauthorized"
        if attempt == 0:
            adb(["disconnect", serial], timeout=8)
            adb(["connect", serial], timeout=12)
    return "offline"


def vtuple(v):
    try:
        return tuple(int(x) for x in re.findall(r"\d+", v or "")[:3])
    except ValueError:
        return ()


# --------------------------------------------------------------------------- TV list
def load_tvs():
    try:
        with open(CONF, encoding="utf-8") as f:
            raw = json.load(f).get("tvs", [])
    except (OSError, ValueError):
        raw = []
    out, seen = [], set()
    for t in raw:
        host = str(t.get("host", "")).strip()
        if not host:
            continue
        name = str(t.get("name") or host).strip()[:40]
        tid = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "tv"
        while tid in seen:
            tid += "-2"
        seen.add(tid)
        out.append({"id": tid, "name": name, "host": host, "serial": host if ":" in host else host + ":5555"})
    return out


def save_tvs(tvs):
    with open(CONF, "w", encoding="utf-8") as f:
        json.dump({"tvs": [{"name": t["name"], "host": t["host"]} for t in tvs]}, f, indent=2)


def tv_by_id(tid):
    for t in load_tvs():
        if t["id"] == tid:
            return t
    raise Fail("Unknown TV")


# --------------------------------------------------------------------------- status
def section(out, name):
    m = re.search(r"^@" + name + r"\n(.*?)(?=^@)", out + "\n@", re.S | re.M)
    return m.group(1).strip() if m else ""


def tv_status(tv):
    serial = tv["serial"]
    with lock_for(serial):
        problem = connect(serial)
        if problem:
            return {"ok": False, "problem": problem, "hint": HINTS[problem]}
        out = sh(serial, "echo @w; dumpsys power | grep -m1 mWakefulness=; echo @u; cat /proc/uptime; "
                         "echo @v; dumpsys package %s | grep -m1 versionName; echo @m; getprop ro.product.manufacturer; "
                         "getprop ro.product.model; echo @a; settings get secure enabled_accessibility_services; "
                         "echo @d; df /data | tail -1; echo @x; dumpsys activity services %s | grep -c KeyRemapService; "
                         "echo @dns; settings get global private_dns_mode; settings get global private_dns_specifier; echo @end"
                 % (PKG, PKG))
        s = {"ok": True}
        w = re.search(r"mWakefulness=(\w+)", section(out, "w"))
        s["power"] = w.group(1) if w else "?"
        try:
            s["uptime_s"] = int(float(section(out, "u").split()[0]))
        except (ValueError, IndexError):
            pass
        v = re.search(r"versionName=(\S+)", section(out, "v"))
        s["version"] = v.group(1) if v else None
        s["model"] = " ".join(section(out, "m").split("\n")).strip()
        s["helper_enabled"] = PKG in section(out, "a")
        s["helper_running"] = section(out, "x").strip() not in ("", "0")
        d = section(out, "d").split()
        if len(d) >= 4 and d[3].isdigit():
            s["storage_free_mb"] = int(d[3]) // 1024
        dl = section(out, "dns").split("\n")
        s["dns_mode"], s["dns_host"] = (dl + ["", ""])[0].strip(), (dl + ["", ""])[1].strip()
        if s["version"] and vtuple(s["version"]) >= NEEDS_STATUS:
            js = sh(serial, "am broadcast -a com.broom.launcher.STATUS -n %s >/dev/null 2>&1; cat %s/broom-status.json" % (RECV, EXT))
            try:
                s["broom"] = json.loads(js)
            except ValueError:
                s["broom_error"] = "Broom didn't return its status"
        s["has_status"] = "broom" in s
        return s


_latest = {"t": 0, "v": None}


def latest_release(force=False):
    if not force and _latest["v"] and time.time() - _latest["t"] < 600:
        return _latest["v"]
    try:
        req = urllib.request.Request("https://api.github.com/repos/%s/releases/latest" % REPO,
                                     headers={"User-Agent": "BroomConsole", "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(req, timeout=12) as r:
            j = json.load(r)
        asset = next((a for a in j.get("assets", []) if a.get("name") == "BroomHome.apk"), None)
        sha = None
        if asset and str(asset.get("digest") or "").startswith("sha256:"):
            sha = asset["digest"][7:]
        if not sha:
            m = re.search(r"SHA-256:\s*`?([0-9a-f]{64})", j.get("body") or "")
            sha = m.group(1) if m else None
        v = {"tag": j.get("tag_name"), "version": (j.get("tag_name") or "").lstrip("v"),
             "url": asset and asset.get("browser_download_url"), "sha256": sha}
        _latest.update(t=time.time(), v=v)
        return v
    except Exception as e:  # offline, rate limit...
        return {"error": "Couldn't reach GitHub: %s" % e}


# --------------------------------------------------------------------------- actions
def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 16), b""):
            h.update(b)
    return h.hexdigest()


def get_apk(source):
    if source == "local":
        if not os.path.isfile(LOCAL_APK):
            raise Fail("There's no local build at launcher/build/BroomHome.apk.")
        return LOCAL_APK, "the local build"
    rel = latest_release(force=True)
    if not rel.get("url"):
        raise Fail(rel.get("error") or "The latest release has no BroomHome.apk.")
    path = os.path.join(DATA, "apk", "BroomHome-%s.apk" % re.sub(r"[^\w.-]", "", rel["tag"] or "latest"))
    if not os.path.isfile(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        req = urllib.request.Request(rel["url"], headers={"User-Agent": "BroomConsole"})
        with urllib.request.urlopen(req, timeout=60) as r, open(path + ".part", "wb") as f:
            shutil.copyfileobj(r, f)
        os.replace(path + ".part", path)
    if rel.get("sha256") and file_sha256(path) != rel["sha256"]:
        os.remove(path)
        raise Fail("The downloaded APK doesn't match the release's SHA-256, so it wasn't installed.")
    return path, rel["tag"]


def broadcast(serial, action):
    return sh(serial, "am broadcast -a com.broom.launcher.%s -n %s" % (action, RECV))


def read_prefs(serial):
    js = sh(serial, "am broadcast -a com.broom.launcher.BACKUP -n %s >/dev/null 2>&1; cat %s/broom-settings.json" % (RECV, EXT))
    try:
        return json.loads(js)
    except ValueError:
        raise Fail("Couldn't read Broom's settings from the TV.")


def write_prefs(serial, version, values):
    """values: {key: {"t": type, "v": text}}. Broom's RESTORE merges, so only these keys change."""
    fd, tmp = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(values, f, ensure_ascii=False)
        code, out, err = adb(["push", tmp, EXT + "/broom-settings.json"], serial, timeout=30)
        if code != 0:
            raise Fail("Couldn't copy the settings to the TV: " + (out + err).strip()[-200:])
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass
    res = broadcast(serial, "RESTORE")
    if "restored" not in res:
        raise Fail("Broom didn't accept the settings: " + res[-200:])
    broadcast(serial, "BACKUP")                       # leave a full copy on the TV again
    if vtuple(version) >= NEEDS_STATUS:
        broadcast(serial, "RELOAD")


def tv_version(serial):
    v = re.search(r"versionName=(\S+)", sh(serial, "dumpsys package %s | grep -m1 versionName" % PKG))
    return v.group(1) if v else None


def backup_dir(tv):
    return os.path.join(DATA, "backups", tv["id"])


def do_action(tv, body):
    action = body.get("action")
    serial = tv["serial"]
    with lock_for(serial):
        problem = connect(serial)
        if problem:
            raise Fail(HINTS[problem])
        version = tv_version(serial)
        new = vtuple(version) >= NEEDS_STATUS

        if action == "install":
            path, label = get_apk(body.get("source"))
            code, out, err = adb(["install", "-r", path], serial, timeout=240)
            if "Success" not in out:
                text = out + err
                if "VERSION_DOWNGRADE" in text:
                    raise Fail("This TV already has a newer Broom than %s, so nothing was installed." % label)
                if "UPDATE_INCOMPATIBLE" in text or "SIGNATURES" in text:
                    raise Fail("The Broom on this TV was signed with a different key (a self-built copy). Updating needs "
                               "'adb uninstall com.broom.launcher' first, which loses Broom's settings: back them up, then re-run the installer.")
                m = re.search(r"Failure \[[^\]]+\]", text)
                raise Fail("Install failed: " + (m.group(0) if m else text.strip()[-200:]))
            time.sleep(6)
            v2 = tv_version(serial)
            running = sh(serial, "dumpsys activity services %s | grep -c KeyRemapService" % PKG).strip() not in ("", "0")
            return {"msg": "Installed %s: Broom %s. Button helper %s." % (label, v2, "running" if running else
                                                                         "not running yet (it usually starts within a minute)")}
        if action == "screenshot":
            code, png, err = adb(["exec-out", "screencap", "-p"], serial, timeout=30, binary=True)
            if not png.startswith(b"\x89PNG"):
                raise Fail("The TV didn't return a screenshot (it may be in standby).")
            os.makedirs(os.path.join(DATA, "shots"), exist_ok=True)
            name = "%s-%s.png" % (tv["id"], time.strftime("%Y%m%d-%H%M%S"))
            with open(os.path.join(DATA, "shots", name), "wb") as f:
                f.write(png)
            return {"msg": "Screenshot saved to console/data/shots/" + name, "img": "/shots/" + name}
        if action == "popups":
            sh(serial, "am broadcast -a com.broom.launcher.PULSE_TEST -p %s" % PKG)
            return {"msg": "Showing each pop-up type once, about 10 s apart (over any app; not on Broom's home screen)."}
        if action == "reload":
            if not new:
                raise Fail("Needs Broom 18.3 or newer.")
            broadcast(serial, "RELOAD")
            return {"msg": "Home redraws now if it's on screen, otherwise next time it shows."}
        if action == "dns_adguard":
            sh(serial, "settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard-dns.com")
            return {"msg": "Private DNS is AdGuard. Broom switches to Cloudflare by itself while AdGuard is down." +
                           ("" if new else " (Update Broom to 18.1+ for the Cloudflare backup.)")}
        if action == "dns_off":
            sh(serial, "settings delete global private_dns_mode; settings delete global private_dns_specifier")
            return {"msg": "Private DNS is off: the TV uses the router's DNS again."}
        if action == "restart":
            adb(["reboot"], serial, timeout=15)
            return {"msg": "Restarting. It takes about a minute. On a Xiaomi, ADB can come back switched off afterwards."}
        if action == "backup":
            prefs = read_prefs(serial)
            os.makedirs(backup_dir(tv), exist_ok=True)
            name = time.strftime("%Y%m%d-%H%M%S") + ".json"
            with open(os.path.join(backup_dir(tv), name), "w", encoding="utf-8") as f:
                json.dump(prefs, f, ensure_ascii=False, indent=1)
            return {"msg": "Backed up %d settings to console/data/backups/%s/%s" % (len(prefs), tv["id"], name)}
        if action == "restore":
            name = str(body.get("file", ""))
            if not re.match(r"^\d{8}-\d{6}\.json$", name):
                raise Fail("Pick a backup from the list.")
            with open(os.path.join(backup_dir(tv), name), encoding="utf-8") as f:
                prefs = json.load(f)
            write_prefs(serial, version, prefs)
            return {"msg": "Restored %d settings from %s." % (len(prefs), name)}
        raise Fail("Unknown action")


# --------------------------------------------------------------------------- settings
def coerce(key, value):
    """Validated {"t", "v"} for a shared key, or Fail."""
    if key in KEYS:
        _, t, _, label, *opt = KEYS[key]
        if t == "b":
            if not isinstance(value, bool):
                raise Fail(label + ": expected on / off")
            return {"t": "b", "v": "true" if value else "false"}
        if t == "i":
            if not isinstance(value, int) or isinstance(value, bool):
                raise Fail(label + ": expected a number")
        if opt and value not in [o[0] for o in opt[0]]:
            raise Fail(label + ": not one of the choices")
        return {"t": t, "v": str(value)}
    if key == "favs":
        if not isinstance(value, list) or not 1 <= len(value) <= 40 or not all(isinstance(p, str) and PKG_RE.match(p) for p in value):
            raise Fail("Favourites: expected 1 to 40 app package names")
        return {"t": "s", "v": ",".join(dict.fromkeys(value))}
    if key in ("wl_india", "wl_us"):
        if not isinstance(value, list) or not 1 <= len(value) <= 60:
            raise Fail("Watchlist: expected 1 to 60 stocks")
        rows = []
        for it in value:
            if not (isinstance(it, list) and len(it) == 2 and isinstance(it[0], str) and SYM_RE.match(it[0].strip())
                    and isinstance(it[1], str) and 0 < len(it[1].strip()) <= 40):
                raise Fail("Watchlist: each line needs a symbol (like TCS.NS or NVDA) and a name")
            rows.append([it[0].strip().upper(), it[1].strip()])
        return {"t": "s", "v": json.dumps(rows, ensure_ascii=False, separators=(",", ":"))}
    raise Fail("That setting can't be changed from the Console: " + key)


def get_settings(tv):
    serial = tv["serial"]
    with lock_for(serial):
        problem = connect(serial)
        if problem:
            return {"ok": False, "hint": HINTS[problem]}
        prefs = read_prefs(serial)
        values, set_keys = {}, []
        for key, row in KEYS.items():
            t, default = row[1], row[2]
            if key in prefs:
                set_keys.append(key)
                raw = prefs[key].get("v")
                values[key] = (raw == "true") if t == "b" else (int(raw) if t == "i" and re.match(r"^-?\d+$", raw or "") else raw)
            else:
                values[key] = default
        return {"ok": True, "values": values, "set": set_keys, "version": tv_version(serial)}


def put_settings(body):
    values = body.get("values") or {}
    if not isinstance(values, dict) or not values:
        raise Fail("Nothing to save")
    clean = {k: coerce(k, v) for k, v in values.items()}
    results = {}
    for tid in body.get("tvs") or []:
        tv = tv_by_id(tid)
        try:
            with lock_for(tv["serial"]):
                problem = connect(tv["serial"])
                if problem:
                    raise Fail(HINTS[problem])
                write_prefs(tv["serial"], tv_version(tv["serial"]), clean)
            results[tid] = {"ok": True, "msg": "Saved %d setting%s" % (len(clean), "" if len(clean) == 1 else "s")}
        except Fail as e:
            results[tid] = {"ok": False, "msg": str(e)}
    return {"results": results}


def list_backups(tv):
    d = backup_dir(tv)
    out = []
    if os.path.isdir(d):
        for n in sorted(os.listdir(d), reverse=True):
            if re.match(r"^\d{8}-\d{6}\.json$", n):
                try:
                    with open(os.path.join(d, n), encoding="utf-8") as f:
                        count = len(json.load(f))
                except (OSError, ValueError):
                    count = None
                out.append({"file": n, "settings": count})
    return {"backups": out}


def adb_network_devices():
    """TVs adb is already connected to over Wi-Fi: [(serial, name)]."""
    try:
        out = adb(["devices", "-l"], timeout=8)[1]
    except Fail:
        return []
    found = []
    for l in out.splitlines()[1:]:
        if re.match(r"^\d+\.\d+\.\d+\.\d+:\d+\s+device\b", l):
            serial = l.split()[0]
            m = re.search(r"model:(\S+)", l)
            found.append((serial, m.group(1).replace("_", " ") if m else serial.split(":")[0]))
    return found


def adb_suggestions(tvs):
    known = {t["serial"] for t in tvs}
    return [{"host": s[:-5] if s.endswith(":5555") else s, "name": n} for s, n in adb_network_devices() if s not in known]


def first_run_tvs():
    """No tvs.json yet: start the list with the TVs adb already knows, so nothing has to be typed."""
    if os.path.exists(CONF) or not ADB:
        return
    found = adb_network_devices()
    if found:
        save_tvs([{"name": n, "host": s[:-5] if s.endswith(":5555") else s} for s, n in found])
        print("Added %d TV%s that adb already knows: %s" % (len(found), "" if len(found) == 1 else "s", ", ".join(n for _, n in found)))


def load_key(new):
    path = os.path.join(DATA, "key.txt")
    if not new:
        try:
            with open(path, encoding="utf-8") as f:
                k = f.read().strip()
            if re.match(r"^[A-Za-z0-9_-]{24,}$", k):
                return k
        except OSError:
            pass
    k = secrets.token_urlsafe(24)
    os.makedirs(DATA, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(k)
    return k


# --------------------------------------------------------------------------- web
class Handler(BaseHTTPRequestHandler):
    server_version = "BroomConsole"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def send(self, code, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        elif isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        if ctype.startswith("text/html"):
            self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
                                                        "img-src 'self'; connect-src 'self'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)

    def guard(self, q):
        host = (self.headers.get("Host") or "").lower()
        if host not in ("127.0.0.1:%d" % PORT, "localhost:%d" % PORT):      # blocks DNS-rebinding
            self.send(403, {"error": "Forbidden"})
            return False
        key = self.headers.get("X-Broom-Key") or (q.get("k") or [""])[0]
        if not secrets.compare_digest(key, KEY):
            self.send(403, {"error": "Open the address printed in the terminal; it contains the key."})
            return False
        return True

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        if u.path == "/":
            if not self.guard(q):
                return
            with open(UI, encoding="utf-8") as f:
                return self.send(200, f.read(), "text/html; charset=utf-8")
        if not self.guard(q):
            return
        try:
            if u.path == "/api/tvs":
                tvs = load_tvs()
                return self.send(200, {"tvs": [{k: t[k] for k in ("id", "name", "host")} for t in tvs], "adb": bool(ADB),
                                       "adb_hint": None if ADB else HINTS["noadb"], "suggest": adb_suggestions(tvs) if ADB else [],
                                       "local_apk": os.path.isfile(LOCAL_APK), "schema": SCHEMA})
            if u.path == "/api/latest":
                return self.send(200, latest_release(force=(q.get("force") or [""])[0] == "1"))
            tid = (q.get("tv") or [""])[0]
            if u.path == "/api/status":
                return self.send(200, tv_status(tv_by_id(tid)))
            if u.path == "/api/settings":
                return self.send(200, get_settings(tv_by_id(tid)))
            if u.path == "/api/backups":
                return self.send(200, list_backups(tv_by_id(tid)))
            m = re.match(r"^/shots/([\w.-]+\.png)$", u.path)
            if m and os.path.isfile(os.path.join(DATA, "shots", m.group(1))):
                with open(os.path.join(DATA, "shots", m.group(1)), "rb") as f:
                    return self.send(200, f.read(), "image/png")
            self.send(404, {"error": "Not found"})
        except Fail as e:
            self.send(400, {"error": str(e)})
        except Exception as e:
            self.send(500, {"error": "Something went wrong: %s" % e})

    def do_POST(self):
        u = urlparse(self.path)
        if not self.guard(parse_qs(u.query)):
            return
        if not (self.headers.get("Content-Type") or "").startswith("application/json"):
            return self.send(415, {"error": "JSON only"})
        n = int(self.headers.get("Content-Length") or 0)
        if n > 256 * 1024:
            return self.send(413, {"error": "Too large"})
        try:
            body = json.loads(self.rfile.read(n) or b"{}")
            if u.path == "/api/action":
                return self.send(200, do_action(tv_by_id(body.get("tv")), body))
            if u.path == "/api/settings":
                return self.send(200, put_settings(body))
            if u.path == "/api/tvs/add":
                name, host = str(body.get("name", "")).strip()[:40], str(body.get("host", "")).strip()
                if not re.match(r"^[A-Za-z0-9.\-]+(:\d{2,5})?$", host):
                    raise Fail("Enter the TV's IP address, like 192.168.1.50")
                tvs = load_tvs()
                if any(t["host"] == host for t in tvs):
                    raise Fail("That TV is already in the list")
                tvs.append({"name": name or host, "host": host})
                save_tvs(tvs)
                return self.send(200, {"ok": True})
            if u.path == "/api/tvs/remove":
                save_tvs([t for t in load_tvs() if t["id"] != body.get("tv")])
                return self.send(200, {"ok": True})
            self.send(404, {"error": "Not found"})
        except Fail as e:
            self.send(400, {"error": str(e)})
        except ValueError:
            self.send(400, {"error": "Bad request"})
        except Exception as e:
            self.send(500, {"error": "Something went wrong: %s" % e})


def main():
    global ADB, PORT, KEY
    ap = argparse.ArgumentParser(description="Broom Console: check and control your TVs from one page.")
    ap.add_argument("--port", type=int, default=8642)
    ap.add_argument("--no-open", action="store_true", help="don't open the browser")
    ap.add_argument("--new-key", action="store_true", help="make a new page key (old bookmarks stop working)")
    a = ap.parse_args()
    ADB = find_adb()
    KEY = load_key(a.new_key)
    first_run_tvs()
    server = None
    for p in range(a.port, a.port + 10):
        try:
            server = ThreadingHTTPServer(("127.0.0.1", p), Handler)
            PORT = p
            break
        except OSError:
            continue
    if not server:
        sys.exit("No free port between %d and %d." % (a.port, a.port + 9))
    server.daemon_threads = True
    url = "http://127.0.0.1:%d/?k=%s" % (PORT, KEY)
    print("Broom Console is running. Open this address (it only works on this computer):\n\n  %s\n" % url)
    print("adb: %s" % (ADB or "NOT FOUND - " + HINTS["noadb"]))
    print("Press Ctrl+C to stop.")
    if not a.no_open:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
