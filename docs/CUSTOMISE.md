# Make it yours

Broom is plain Java, about 8,000 lines with no libraries, and each screen has its own file. All source is in `launcher/src/com/broom/launcher/`. Edit a file, then run `./build.sh` and `adb install -r build/BroomHome.apk`.

## Things you can change without touching code
- **Settings → Features:** markets, cricket, football, Panchang and each news category on or off.
- **Briefing → Watchlist:** add, remove, reorder or rename stocks, and set alert thresholds.
- **Theme button:** Auto or any scene. Festival decorations are on the same screen.
- **Screensaver button:** style, timers, "Briefing inside every screensaver", night mode.
- **Weather → Change area:** use a typed place instead of the TV's location.

## Where things live in the code

| Want to change… | File | Look for |
|---|---|---|
| Market groups, index / stock lists, ticker symbols | `Briefing.java` | `GROUPS`, `TICKER` (Yahoo Finance symbols, e.g. `RELIANCE.NS`, `AAPL`, `^NSEI`) |
| News feeds and categories | `Briefing.java` | `FEEDS` (any RSS URL), `SECTIONS`, `SECTION_TITLES` |
| Which features can be switched off | `Features.java` | `KEYS`, `LABELS` |
| Default favourite apps | `AppCatalog.java` | `DEFAULT_FAVS` (package names; bump `FAVS_VERSION` to re-apply) |
| Greetings (English / Hindi / Gujarati) | `Theme.java` | `greeting()` |
| Facts and quotes screensaver | `assets/quotes.json` | `{"k": kind, "t": text, …}` entries (kinds: `fact`, `quote`, `hi`, `gu`, `sa`) |
| Themes and their colours | `Theme.java` | `DAWN`, `DAY` … and the pack themes |
| Painted theme scenes | `PackScenes.java`, `Scenic.java` | one `draw…` method per scene |
| Festival days | `Panchang.java` | `festival()` (tithi-based) |
| Festival decorations | `Festive.java` | `kind()`, `draw()`, `greeting()` |
| Panchang language (Gujarati amanta by default) | `Panchang.java`, `Vedic.java` | `gujarati()`, `MONTH_GU`, `VAAR_GU` |
| Fallback location when there is none | `Vedic.java` | `return new double[]{19.07, 72.88}` |
| Photo screensaver searches | `Photos.java` | search terms per pack, `BLOCK` (words to filter out) |
| Animated screensavers | `DreamView.java`, `Nebula.java` | `MODES`, one class per style |
| Weather and the walk / jog score | `Weather.java` | `comfort()` |
| Remote-button behaviour | `KeyRemapService.java`, `res/xml/remap.xml` | |
| Stats "before" numbers | `assets/baseline.json` | regenerate with `make_baseline.py` from your own ADB snapshots |

## Ideas
- Swap the cricket feed for your sport, or the Indian indices for your country's
- Add your language's greeting and calendar
- New theme scenes: copy an existing `draw…` method in `PackScenes.java`
- A family photo pack: the USB photos screensaver reads any folder of JPGs on a stick

## Testing without the remote
```bash
adb shell am start -n com.broom.launcher/.MainActivity --es theme dusk     # preview a theme
adb shell am start -n com.broom.launcher/.MainActivity --es fest diwali    # preview a festival
adb shell am start -n com.broom.launcher/.DreamPreviewActivity --es mode aurora
adb shell am broadcast -a com.broom.launcher.SCORE_DEMO                    # score pop-up (over another app)
adb shell am broadcast -a com.broom.launcher.MARKET_DEMO                   # market alert pop-up
```
Screenshot: `adb exec-out screencap -p > shot.png`.
