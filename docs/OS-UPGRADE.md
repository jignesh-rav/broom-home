# Can this Mi TV run a newer OS? Research report

*Research only. Nothing was flashed, rebooted or changed on the TV. Date: 2026-11-15.*

## TL;DR

**No. It isn't realistically possible, and it isn't worth trying.**

- **No official update.** `PI.6036` (software 21.7.15.6036, built 15 Jul 2021, security patch 2021-06-05) is the last firmware Xiaomi published for `dangal`. Xiaomi never released Android TV 10, 11 or 12 for this model.
- **No custom ROM or port.** I found no custom ROM, LineageOS build, ATV 10-12 port or Google TV port for `dangal` or for any Amlogic T950X/TXLX smart-TV board.
- **Locked boot chain.** The boot chain is locked and signed (`AMLSECU!` header). On XDA, people who own this exact board could not even get root.
- **Weak hardware.** 1 GB RAM (`ro.config.low_ram=true`), a 32-bit-only userspace and a 720p panel all hold it back. A newer Android would run slower than Pie, not faster.
- **Better route:** keep the stock OS, debloated, with the lightweight launcher. For a modern OS, plug a cheap current streamer into HDMI.

## 1. What this TV actually is (checked over ADB, read-only)

| Property | Value | Why it matters |
|---|---|---|
| Model / codename | `MiTV-AXSO0` / `dangal`, sold as **Mi TV 4C Pro 32" / 4A Pro 32" (L32M5-AN)** | The same `dangal` firmware also covers the 32/40/43 Horizon and Pro models in India |
| SoC | `ro.board.platform=txlx`, board `TPD.T950X.PB791` (Amlogic T950X class), 4× Cortex-A53 (`CPU part 0xd03`) | A 2018-era TV SoC. No community ROM work exists for it |
| ABI | `armeabi-v7a,armeabi` only (`abilist64` empty) | 32-bit Android build. Newer ATV releases and apps are moving to 64-bit |
| RAM | 1,014,724 kB total, ~280 MB available at idle; `ro.config.low_ram=true` | Already the Android Go-style low-RAM profile |
| Panel | 1280×720 @ 60 Hz, single mode | A newer OS adds nothing visible |
| Build | `Xiaomi/dangal/dangal:9/PI/6036:user/release-keys`, security patch 2021-06-05 | Last official build |
| Boot security | `ro.boot.verifiedbootstate=green`, `veritymode=enforcing`, AVB 1.1, `ro.secure=1`, `ro.debuggable=0`, `ro.build.type=user` | Locked, verified boot. Unsigned images will not boot |
| Bootloader | `ro.build.expect.bootloader=01.01.180822.145544` (U-Boot) | 2018 bootloader; no unlock command exposed |
| Treble | `ro.treble.enabled=true`, `ro.vndk.version=28` | A GSI could boot in theory, but it can't be flashed (see section 3) |
| DRM | Widevine HAL (`android.hardware.drm@1.1-service.widevine`), PlayReady and `libnetflixplugin.so` in `/vendor/lib/mediadrm`; `FLAG_SECURE` and protected buffers on the display; `com.netflix.ninja` installed | Netflix-certified with hardware DRM. Keys live in device-unique partitions (`tee`/`persist`). Any reflash puts L1/Netflix at risk |
| Storage | `/` 1.7 GB (82% used), `/vendor` 173 MB, `/data` 4.4 GB | Too small for a heavier modern system image |
| Update app | `com.xiaomi.mitv.updateservice` 2.21.33, last updated 2026-11-04 | Only the updater app was updated (it updates itself). The OS build has not changed since 2021 |

## 2. Question 1: is there an official Xiaomi OTA beyond PI.6036?

**No.**
- Xiaomi's own open-source branch for this device, `dangal-p-oss` on MiCode/MiTV_OpenSource, is Android P (9) only. A community mirror of that branch, which includes the official firmware, names **build 6036** as the `dangal` firmware for the 32 Pro, 32 Horizon, 40, 40 Horizon, 43 Horizon and 43 Pro models.
- Xiaomi's update history for this family ends at Android 9 Pie (rolled out to the 4A Pro and 4C Pro in 2019) plus PatchWall 3.0 (2020). I found no Android TV 10 or 11 announcement for any 4A, 4C or 4X model.
- **How to check yourself (safe):** on the TV, open Settings → About → System update (served by `com.xiaomi.mitv.updateservice`). It should report "latest version". If it ever offers an update, an official signed OTA is the only safe upgrade path, so leave that package enabled (it's on the debloat never-touch list).
- **Do not cross-flash a sibling build.** The Full-HD sibling `MiTV-AXSO1` (`dangalFHD`) runs `PI.6157`. It is a different board and panel, so flashing its firmware here would almost certainly brick the TV or break the display. An XDA post also mentions a later hardware revision that shipped 64-bit Android, but there's no evidence that its firmware works on this unit.

## 3. Question 2: community custom ROMs, ATV 10-12, Google TV ports

**There aren't any for `dangal`.**

| Option | Status for this TV | What would break |
|---|---|---|
| Custom ROM (LineageOS ATV, etc.) | **None exists.** LineageOS ATV builds for Amlogic target set-top boxes on G12/SM1 SoCs (S905X2/X3/X4), not TV mainboards with tuners and panels | Everything TV-specific |
| Android TV GSI (Treble) | Possible in theory (Treble on, VNDK 28), but **can't be flashed**: system is covered by AVB and dm-verity on a locked bootloader. A GSI would also lack the Xiaomi/droidlogic TV layer | HDMI inputs (`com.droidlogic.tvinput`), picture-quality engine, Xiaomi BT remote pairing and voice, PatchWall, tuner, likely Widevine L1 and Netflix certification, Chromecast certification |
| Google TV port | **None.** Google TV is licensed per device, and 1 GB RAM is below what it's typically shipped with | Wouldn't be certified; Play services and casting would be unreliable |
| Firmware from another Mi TV (4A/4X/4S) | `machuca` (4S/4A Europe) is **MediaTek**, a different SoC, and would hard-brick the TV. Other `dangal`-family boards ship the same build 6036, so there's nothing newer to borrow | Total brick |
| Root / Magisk only (still Pie) | **Not achieved.** On XDA, an owner of this exact board (Oct–Nov 2025) found boot and recovery are `AMLSECU!`-signed and probably **encrypted**. Magisk can't patch them, and an experienced developer replied that even `bootloader.img` is unreadable. No working method has been published | Not applicable |

The same thread includes a useful warning from another member: rooting this 2019 T950X TV "is not likely to reduce lagginess. It is old and slow." They suggested an external streaming box instead.

## 4. Question 3: bootloader unlock, brick risk, recovery path

- **Unlock:** Xiaomi offers no unlock tool or `fastboot oem unlock` for Mi TVs; the Mi Unlock tool is for phones. The bootloader enforces Amlogic secure boot (signed images, `verifiedbootstate=green`). **It can't be unlocked by any method I could find.**
- **Fastboot access:** there's no button combination. The only reported way into fastboot is the **UART serial console** on the mainboard (open the TV, solder or clip a 3.3 V USB-UART adapter, interrupt U-Boot). The XDA poster recovered from a self-inflicted hard brick this way, by reflashing the stock signed `boot.img` and `recovery.img` from the OTA package. In fastboot, a locked bootloader still accepts only signed images.
- **Factory image availability:**
  - The last official OTA package (6036) is mirrored on GitHub by blue-pine, with images extracted on the XDA thread. Treat third-party uploads as unverified.
  - I found **no public Amlogic USB Burning Tool image** (`aml_upgrade_package.img`) for `dangal`. Without one, recovering from a corrupted bootloader means UART, or a mainboard replacement (sold as "L32M5-AN TPD.T950X.PB791 motherboard").
  - A burn image would also overwrite or erase the `tee`/`persist` partitions unless handled carefully. Losing them means Widevine L1 drops to L3 permanently (SD-only Netflix and Prime) and Netflix certification can be lost. **No one has published a way to back up those keys on this device.**
- **Brick risk:**

  | Action | Risk |
  |---|---|
  | Official OTA | Low |
  | Re-flashing the same signed 6036 OTA from USB recovery | Low to medium |
  | Anything unsigned or from another model | High: boot loop or hard brick, recoverable only via UART or a new mainboard |

## 5. Question 4: recommendation

**Not worth it. Don't attempt an OS change on this TV.**

1. **Nothing to install.** No newer official build exists and no community ROM exists, so there's nothing to flash.
2. **Locked boot chain.** Even if a ROM existed, the encrypted, signed boot chain blocks it, and the only recovery path means opening the TV for UART.
3. **Big downside.** Likely loss of HDMI and input switching, the Xiaomi remote and voice, picture tuning, and Widevine L1/Netflix. These are exactly the things that must keep working.
4. **No speed gain.** 1 GB RAM and a 32-bit build: Android TV 10-12 have higher memory baselines, so any port would be *slower*. The real speed gains come from what is already happening, debloating plus a lightweight launcher.

**What to do instead:**
- **Keep stock `PI.6036`.** Leave `com.xiaomi.mitv.updateservice` enabled so any surprise official OTA can still arrive, and continue the disable-only debloat with the custom launcher.
- **For a genuinely modern OS** (Google TV, current Netflix/YouTube/apps, AV1, new security patches), plug in a current streamer such as Google TV Streamer, Mi TV Stick 4K, Xiaomi TV Box S (2nd/3rd gen) or a Fire TV Stick. Use the TV as a 720p panel with its own remote over HDMI-CEC. It's cheap, reversible and has zero brick risk.
- **If you still want to experiment** (only on a TV you can afford to lose), the route would be UART access → dump `tee`/`persist` → wait for the XDA `dangal` root effort to produce something. As of Nov 2025 that effort had no working method.

## Sources

- XDA thread, *Magisk fails to patch ramdisk of AMLSECU! signed boot or recovery of 'dangal' [MITV 4C Pro 32 inch or L32M5-AN]* (Oct–Nov 2025): signed and encrypted boot, UART-only fastboot, no root, image dumps — https://xdaforums.com/t/magisk-fails-to-patch-ramdisk-of-amlsecu-signed-boot-or-recovery-of-dangal-mitv-4c-pro-32-inch-or-l32m5-an-no-ramdisk-in-boot-so-patch-recovery.4765362/
- blue-pine, *MiTV_OpenSource_dangal-p-oss (Mi TV 4C Pro 32, Android 9 Pie)*: official source and firmware mirror, build 6036, list of covered models — https://github.com/blue-pine/MiTV_OpenSource_dangal-p-oss_Mi-TV-4C-Pro-32-inch-Android-9-Pie
- Xiaomi official open source, branch `dangal-p-oss` (Android P only) — https://github.com/MiCode/MiTV_OpenSource/tree/dangal-p-oss
- Older Android 8.1 dumps of `dangal` — https://github.com/XenonTheInertG-CI/xiaomi_dangal_dump and https://gitlab.com/Android-Dumps/xiaomi/dangal
- cleanyourtv, MiTV-AXSO0 (Android 9, PI.6036) and AXSO1 `dangalFHD` (PI.6157) examples — https://github.com/Gathin23/cleanyourtv/pull/25 and https://github.com/Gathin23/cleanyourtv/pull/2665
- Android Pie rollout to the 4A Pro / 4C Pro (2019) — https://www.fonearena.com/blog/290392/xiaomi-mi-tv-4a-pro-mi-tv-4c-pro-android-pie-mi-tv-4a.html and https://telecomtalk.info/mi-tv-android-pie/218503/
- PatchWall 3.0 for the Mi TV 4/4A/4C/4X Pro (2020) — https://www.xda-developers.com/xiaomi-mi-tv-4-4a-4c-4x-pro-patchwall-3-0-update-india-disney-hotstar-integration-more/
- LineageOS 18.1 (Android 11) for Amlogic G12/SM1 boxes, which shows community ATV work targets set-top-box SoCs, not TV boards — https://www.xda-developers.com/lineageos-18-1-android-tv-11-walmart-onn-4k-dynalink-tv-box
- Mi TV Stick 4K brick and recovery notes (shows how Amlogic Xiaomi bricks behave) — https://github.com/RodrigoDeveloperX/xiaomi-mi-tv-stick-4k-mdz-27-aa-recovery
