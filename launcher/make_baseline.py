"""make_baseline.py <before_dir> [after_dir] -> writes assets/baseline.json from ADB snapshots."""
import json, re, sys, os
NAMES = {"com.netflix.ninja":"Netflix","com.google.android.tvlauncher":"Stock Android TV home","com.mitv.tvhome.atv":"PatchWall",
 "com.amazon.amazonvideo.livingroom":"Prime Video","com.google.android.gms":"Google Play services","system":"Android system",
 "com.google.android.katniss":"Google Assistant","com.xiaomi.mitv.updateservice":"Xiaomi updater","com.android.vending":"Play Store",
 "com.android.systemui":"System UI","com.android.tv.settings":"Settings","com.google.android.apps.mediashell":"Chromecast",
 "com.android.tv":"Live TV","com.google.android.tvrecommendations":"Recommendations (ads)","com.google.android.inputmethod.latin":"Keyboard",
 "com.jio.media.stb.ondemand":"JioCinema","com.broom.launcher":"Broom Home (new)","com.miui.tv.analytics":"Xiaomi analytics"}
def snap(d):
    if not d: return None
    r = lambda f: open(os.path.join(d, f), encoding="utf8", errors="ignore").read()
    mem = r("proc-meminfo.txt"); g = lambda k: int(re.search(k + r":\s+(\d+)", mem).group(1)) // 1024
    disk = r("diskstats.txt"); df = int(re.search(r"Data-Free: (\d+)K", disk).group(1)) // 1024
    allp = len([l for l in r("packages-all.txt").splitlines() if l.startswith("package:")])
    dis = len([l for l in r("packages-disabled.txt").splitlines() if l.startswith("package:")])
    load = re.search(r"Load: ([\d.]+) / ([\d.]+) / ([\d.]+)", r("cpuinfo.txt"))
    agg = {}; sec = False
    for l in r("meminfo.txt").splitlines():
        if "Total PSS by process" in l: sec = True; continue
        if sec:
            m = re.match(r"\s*([\d,]+)K: (\S+)", l)
            if not m:
                if not l.strip(): break
                continue
            p = m.group(2).split(":")[0]; agg[p] = agg.get(p, 0) + int(m.group(1).replace(",", ""))
    top = sorted(agg.items(), key=lambda x: -x[1])[:10]
    return {"ram_total_mb": g("MemTotal"), "ram_avail_mb": g("MemAvailable"), "data_free_mb": df,
            "pkgs_total": allp, "pkgs_disabled": dis, "pkgs_active": allp - dis,
            "load": load.group(1) if load else "?", "load_all": " / ".join(load.groups()) if load else "?",
            "top": [[NAMES.get(p, p), round(k / 1024)] for p, k in top]}
b = snap(sys.argv[1]); a = snap(sys.argv[2]) if len(sys.argv) > 2 else None
undo = os.path.join(os.path.dirname(os.path.abspath(sys.argv[1])), "undo.txt")
lines = open(undo).read().splitlines() if os.path.exists(undo) else []
prop = open(os.path.join(sys.argv[1], "getprop.txt"), encoding="utf8").read()
pp = lambda k: (re.search(r"\[" + re.escape(k) + r"\]: \[([^\]]*)\]", prop) or [None, "?"])[1]
out = {"device": "%s %s (%s) · Android TV %s" % (pp("ro.product.manufacturer"), pp("ro.product.model"), pp("ro.product.device"), pp("ro.build.version.release")),
       "measured": os.path.basename(os.path.normpath(sys.argv[-1])), "before": b, "after": a,
       "disabled": sum(1 for l in lines if "pm enable" in l), "uninstalled": sum(1 for l in lines if "install-multiple" in l)}
here = os.path.dirname(os.path.abspath(__file__))
json.dump(out, open(os.path.join(here, "assets", "baseline.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
print(json.dumps({k: v for k, v in out.items() if k not in ("before", "after")}), "\nbefore:", b and {k: b[k] for k in b if k != "top"}, "\nafter:", a and {k: a[k] for k in a if k != "top"})
