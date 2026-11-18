# Performance: smooth remote navigation on low-end TVs

A TV redraws its picture 60 times a second, so every frame of an animation has **16.7 ms** to be drawn. When a frame takes longer, it misses the screen refresh and the movement stutters. On a budget TV this is what makes a launcher feel "laggy" when you move the remote, even when the processor and memory have plenty to spare.

From version 18.7, Broom's home screen keeps every frame of a focus move well inside that budget, on both TVs it is tested on.

## Results

Measured on the home screen: 16 focus moves with the remote's arrow keys, repeated 3 times after a warm-up (about 300 frames per row). Frame time is from the screen refresh that started the frame to the frame being finished, taken from Android's own frame statistics.

| TV | Version | Average | Median | 90th percentile | 99th percentile | Frames over 16.7 ms | Smoothness |
|---|---|---|---|---|---|---|---|
| Xiaomi Mi TV 4A Pro, 1 GB, 1280×720 | before (18.6) | 30.8 ms | 27.8 ms | 46.2 ms | 58.6 ms | 99% | ~32 fps |
| | **after (18.7)** | **10.2 ms** | **9.9 ms** | **11.9 ms** | **16.5 ms** | **1%** | **60 fps** |
| TCL 4K (BeyondTV), 2 GB, 1920×1080 | before (18.6) | 44.0 ms | 44.9 ms | 61.8 ms | 67.6 ms | 100% | ~23 fps |
| | **after (18.7)** | **11.2 ms** | **11.4 ms** | **13.2 ms** | **16.7 ms** | **1%** | **60 fps** |

That is **3× faster on the Mi TV and 4× faster on the TCL**. Even the slowest 1% of frames now fits the 16.7 ms budget. The home screen looks exactly as before: the same text shadows, glass tiles and focus glow.

## What changed

Most of the home screen does not move while you navigate: the clock, greeting, weather, ticker, row titles and the side rail. Before, the TV re-drew all of it, including every soft text shadow, for every frame of every focus move. Now Broom draws those parts once, keeps them as ready-made images, and only redraws one when its content actually changes (the clock once a minute, the weather when it updates). Each app tile is kept the same way. A focus move now only combines ready-made pictures and enlarges one tile, which a TV's graphics chip does easily.

The cost is a few megabytes of graphics memory for the stored images, about the size of two full-screen pictures.

**The processor was never the problem.** Broom's own work per frame was under 2 ms before and after. The time went into *drawing*, which is why faster CPUs or freeing RAM alone would not have fixed it.

## A practice every TV launcher should adopt

Caching what does not move and animating only what does is standard practice in Android's own rendering guidance and in game and UI engines. It is easy to miss on TVs, because a design that is smooth on a phone or a developer's TV box can drop to 25–30 fps on the budget TVs most people own. On a TV every navigation is an animation, so this decides how the whole device feels.

The principles, in order of impact:

1. **Measure first, on the slowest TV you support, at its native resolution.** A 1080p screen has 2.25 times the pixels of 720p, and many TV graphics chips are weaker than a mid-range phone's.
2. **Keep static content cached.** Anything that does not change while focus moves (headers, labels, clocks, panels, banners) should be drawn once and reused, not re-drawn each frame.
3. **Know the expensive effects.** Soft shadows on text, blur, live elevation shadows, see-through groups of overlapping views, and rounded clipping are cheap once, and costly 60 times a second. Use them on cached content, not on things being redrawn every frame.
4. **Animate with transforms only.** Focus effects should only scale, fade or move a view, in 100–150 ms. Never change sizes, padding or text on focus, because that forces the whole screen to be laid out again.
5. **Keep the main thread free.** No disk, network or image decoding during navigation. Prepare images at screen size, in the background.
6. **Avoid hidden layers of paint.** Don't stack full-screen backgrounds that cover each other; each one costs a full screen of pixels per frame.
7. **Spend memory carefully.** A cached image costs width × height × 4 bytes. Cache what is on screen, not long scrolling lists.

**Targets that feel smooth on a TV:** 90th percentile under 16.7 ms, 99th percentile under 33 ms, and fewer than 5% of frames over budget during navigation.

## System-level speed-ups (installer default)

On top of Broom's own drawing, the installer tunes three TV settings. It saves the TV's own values first, and `undo` puts them back. Skip them with `-NoSpeedTweaks` / `--no-speed-tweaks`. Broom Console shows each TV's state and offers **Make navigation snappier** for TVs that don't have them yet.

| Setting | What it does | Measured effect |
|---|---|---|
| **Broom compiled ahead of time** | Android normally compiles only the parts of an app it has seen being used; the rest runs slower until it is compiled in the background. Broom is now compiled fully right after install. | Opening the Briefing: Mi TV 1080 → 914 ms (15% faster), TCL 824 → 788 ms (4% faster). Weather: Mi TV 368 → 329 ms (11%), TCL 335 → 323 ms (4%). Light screens (Themes) unchanged. Medians of 4 opens each. |
| **Window and screen-change animations off** | Apps and screens open straight away instead of sliding or fading in. Broom's own focus animations stay, at half length, so moving around still feels fluid. | Removes the ~150 ms transition the TCL played on every screen change (the Mi TV already had them off). |
| **Held keys repeat after 300 ms** (was 400 ms) | Holding an arrow key starts scrolling 100 ms sooner. Long presses (for example the app menu on a tile) also register at 300 ms. | 25% shorter wait before a held key repeats. Apps pick it up at once; the TV's own key-repeat may only follow after its next full restart. |

Compiling ahead of time is redone automatically by the installer and the Console after every Broom update, because Android resets it when an app is updated.

## How to measure any Android TV app

You need ADB (see the README, Step 1) and the app's package name.

1. **Frame statistics.** Clear them, use the app for 10–20 seconds, then read them:
   ```bash
   adb shell dumpsys gfxinfo com.example.app reset
   adb shell dumpsys gfxinfo com.example.app
   ```
   This prints the number of janky frames and the 50th, 90th, 95th and 99th percentile frame times.
2. **Where the time goes.** `adb shell dumpsys gfxinfo com.example.app framestats` lists each recent frame with timestamps for every stage. Comparing them shows whether the app's own code (input, animation, layout) or the drawing is slow.
3. **On screen, while you navigate.** Developer options → **Profile HWUI rendering** → *On screen as bars* (`adb shell setprop debug.hwui.profile visual_bars`). Each bar is a frame; bars above the green line missed the 16.7 ms budget. **Debug GPU overdraw** (`adb shell setprop debug.hwui.overdraw show`) tints areas painted several times per frame. Turn both off afterwards (`setprop ... false`).
4. **Deep dives.** A Perfetto or system trace shows every frame on a timeline. Android's Macrobenchmark library (`FrameTimingMetric`) can run the same navigation test automatically on a real TV after every change.

Always warm the app up first (a cold start includes one-off work), use the same sequence of key presses for before and after, and compare percentiles rather than a single average.

Further reading: [Slow rendering](https://developer.android.com/topic/performance/vitals/render), [Hardware acceleration and view layers](https://developer.android.com/develop/ui/views/graphics/hardware-accel), [Inspect GPU rendering speed and overdraw](https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering).
