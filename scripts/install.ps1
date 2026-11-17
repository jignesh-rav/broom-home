# install.ps1 - set up Broom Home on an Android TV / Google TV over Wi-Fi ADB.
# Safe to run again any time (for example after a system update resets something).
#
#   Step 1  install Broom and make it the Home app and screensaver
#   Step 2  debloat: switch off telemetry, ads and unused apps (asks first; undo turns everything back on)
#
#   .\install.ps1 -Tv 192.168.1.50                    # install + debloat, asking what to switch off
#   .\install.ps1 -Tv 192.168.1.50 -DebloatPreview    # only show what the debloat step would switch off, change nothing
#   .\install.ps1 -Tv 192.168.1.50 -NoDebloat         # install only, leave every other app as it is
#   .\install.ps1 -Tv 192.168.1.50 -Debloat           # no questions: switch off the safe AND the optional packages
#   .\install.ps1 -Tv 192.168.1.50 -AdBlockDns        # also turn on AdGuard private DNS (blocks ads system-wide)
#   .\install.ps1 -Tv 192.168.1.50 -Apk path\to\BroomHome.apk
#
# Without a console to answer questions (e.g. run by a script), only the safe packages are switched off.
param(
    [Parameter(Mandatory = $true)][string]$Tv,
    [string]$Apk,
    [switch]$Debloat,
    [switch]$NoDebloat,
    [switch]$DebloatPreview,
    [switch]$AdBlockDns
)
$ErrorActionPreference = "Continue"
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }
$root = Split-Path $PSScriptRoot -Parent
$interactive = $false
try { $interactive = [Environment]::UserInteractive -and -not [Console]::IsInputRedirected } catch { }

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb not found. Install it with:  winget install Google.PlatformTools   then open a new PowerShell window." -ForegroundColor Red
    exit 1
}
$pkg = "com.broom.launcher"
# call adb.exe by its full path (a PowerShell function named like it would shadow it)
$adbExe = (Get-Command adb -CommandType Application | Select-Object -First 1).Source
function Sh([string]$cmd) { (& $adbExe -s $Tv shell $cmd 2>$null | Out-String).Trim() }
function Ask([string]$q, [string]$default) {
    $a = Read-Host $q
    if (-not $a) { $a = $default }
    return ($a -match '^[yY]')
}

Write-Host "Connecting to $Tv (accept the 'Allow USB debugging' prompt on the TV if it appears)..."
& $adbExe connect $Tv | Out-Host
if ((& $adbExe -s $Tv get-state 2>$null) -ne "device") {
    Write-Host "Could not connect. Check the IP, that the TV is on, and that ADB debugging is enabled (see docs/SETUP.md)." -ForegroundColor Red
    exit 1
}
$brand = ((Sh "getprop ro.product.manufacturer").ToLower() -replace '[^a-z0-9]', '')
$model = Sh "getprop ro.product.model"
Write-Host "TV: $brand $model"

# ---------- debloat plan: what's on this TV from debloat-list.txt (+ debloat-<brand>.txt)
$lists = @("$PSScriptRoot\debloat-list.txt")
if ($brand -and (Test-Path "$PSScriptRoot\debloat-$brand.txt")) { $lists += "$PSScriptRoot\debloat-$brand.txt" }
function Get-Plan {
    $installed = @{}; foreach ($l in ((Sh "pm list packages") -split "`n")) { $installed[$l.Trim()] = 1 }
    $disabled = @{};  foreach ($l in ((Sh "pm list packages -d") -split "`n")) { $disabled[$l.Trim()] = 1 }
    $seen = @{}; $plan = @()
    foreach ($f in $lists) {
        $opt = $false
        foreach ($line in Get-Content $f) {
            if ($line -match '^#\s*---') { $opt = $line -match 'optional'; continue }
            $p = ($line -replace '#.*$', '').Trim()
            if (-not $p) { continue }
            $desc = if ($line -match '#(.*)$') { $Matches[1].Trim() } else { "" }
            $mode = if ($desc -match 'remove-for-user') { "user" } else { "disable" }
            $desc = ($desc -replace 'remove-for-user:?\s*', '').Trim()
            if ($seen[$p]) { continue }; $seen[$p] = 1
            if (-not $installed["package:$p"] -or $disabled["package:$p"]) { continue }
            $plan += [pscustomobject]@{ Group = $(if ($opt) { "opt" } else { "safe" }); Pkg = $p; Mode = $mode; Desc = $desc }
        }
    }
    return ,$plan
}
function Show-Group($plan, [string]$group, [string]$title) {
    $items = @($plan | Where-Object { $_.Group -eq $group })
    if ($items.Count -eq 0) { return }
    Write-Host "  ${title} ($($items.Count)):"
    foreach ($i in $items) { Write-Host ("    {0,-44} {1}" -f $i.Pkg, $i.Desc) }
}
function Switch-Off($item) {
    if ($item.Mode -eq "user") { Write-Host "  remove for user  $($item.Pkg)"; Sh "pm uninstall -k --user 0 $($item.Pkg)" | Out-Null }
    else { Write-Host "  switch off       $($item.Pkg)"; Sh "pm disable-user --user 0 $($item.Pkg)" | Out-Null }
}
$plan = Get-Plan
$listNames = ($lists | ForEach-Object { Split-Path $_ -Leaf }) -join " + "

if ($DebloatPreview) {
    Write-Host ""; Write-Host "Debloat preview for this TV (nothing is changed). Lists: $listNames"
    if ($plan.Count -eq 0) { Write-Host "  Nothing to switch off: everything on the lists is already off or not on this TV." }
    Show-Group $plan "safe" "Safe: telemetry, ads and apps a TV doesn't use"
    Show-Group $plan "opt" "Optional: your call"
    Write-Host "Never touched: remote, HDMI, picture, sound, voice, Chromecast, Play Store and system updates."
    exit 0
}

# ---------- Step 1: install Broom and make it Home
Write-Host ""; Write-Host "== Step 1 of 2: install Broom Home" -ForegroundColor Cyan
if (-not $Apk) {
    $Apk = @("$root\launcher\build\BroomHome.apk", "$PSScriptRoot\BroomHome.apk", "$root\BroomHome.apk") |
           Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $Apk) {
    # No local APK: download the latest release (no build needed)
    $Apk = "$PSScriptRoot\BroomHome.apk"
    Write-Host "Downloading the latest BroomHome.apk from GitHub Releases..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing "https://github.com/jignesh-rav/broom-home/releases/latest/download/BroomHome.apk" -OutFile $Apk
    } catch { $Apk = $null }
}
if (-not $Apk -or -not (Test-Path $Apk)) {
    Write-Host "No APK. Download BroomHome.apk from the GitHub Releases page into this folder (or build it with launcher/build.sh)." -ForegroundColor Red
    exit 1
}

Write-Host "Installing $Apk ..."
& $adbExe -s $Tv install -r $Apk | Out-Host

Write-Host "Granting permissions..."
Sh "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null      # screensaver + remote-button helper
Sh "pm grant $pkg android.permission.ACCESS_FINE_LOCATION" | Out-Null       # weather for your area (rounded to ~1 km)
Sh "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION" | Out-Null
Sh "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE" | Out-Null      # photos from a USB stick
Sh "appops set $pkg WRITE_SETTINGS allow" | Out-Null                        # screensaver timer
Sh "appops set $pkg GET_USAGE_STATS allow" | Out-Null                       # "Recently used" row

Write-Host "Enabling the remote-button helper (sends the Home button to Broom)..."
$svc = "$pkg/$pkg.KeyRemapService"
$a11y = Sh "settings get secure enabled_accessibility_services"
if ($a11y -notmatch "KeyRemapService") {
    $new = if ($a11y -and $a11y -ne "null") { "${a11y}:$svc" } else { $svc }
    Sh "settings put secure enabled_accessibility_services $new" | Out-Null
    Sh "settings put secure accessibility_enabled 1" | Out-Null
}

Write-Host "Setting Broom as the screensaver and the Home app..."
Sh "settings put secure screensaver_components $pkg/.BroomDream" | Out-Null
Sh "settings put secure screensaver_enabled 1" | Out-Null
Sh "cmd package set-home-activity --user 0 $pkg/.MainActivity" | Out-Null
# Switch off the stock home app so Home goes to Broom (undo.ps1 brings it back):
#   Android TV: com.google.android.tvlauncher     Google TV: com.google.android.apps.tv.launcherx
if ($brand -eq "tcl") {
    # TCL switches a disabled stock home back on at every boot: remove it for the user instead (undo restores it)
    Sh "pm uninstall -k --user 0 com.google.android.tvlauncher" | Out-Null
} else {
    Sh "pm disable-user --user 0 com.google.android.tvlauncher" | Out-Null
}
if ((Sh "pm list packages") -match "package:com.google.android.apps.tv.launcherx") {
    Write-Host "Google TV detected: switching off the Google TV home screen (undo.ps1 turns it back on)." -ForegroundColor Yellow
    Sh "pm disable-user --user 0 com.google.android.apps.tv.launcherx" | Out-Null
}
Sh "cmd package set-home-activity --user 0 $pkg/.MainActivity" | Out-Null

# ---------- Step 2: debloat
Write-Host ""; Write-Host "== Step 2 of 2: debloat (switch off what slows the TV down)" -ForegroundColor Cyan
$safe = @($plan | Where-Object { $_.Group -eq "safe" }); $opt = @($plan | Where-Object { $_.Group -eq "opt" })
$done = 0
if ($NoDebloat) {
    Write-Host "Skipped (-NoDebloat). Run the installer again without it any time."
} elseif ($plan.Count -eq 0) {
    Write-Host "Nothing to switch off: everything on the lists is already off or not on this TV."
} else {
    Write-Host "Nothing is deleted: these are only switched off, and undo turns every one back on."
    Write-Host "Never touched: remote, HDMI, picture, sound, voice, Chromecast, Play Store and system updates."
    Show-Group $plan "safe" "Safe: telemetry, ads and apps a TV doesn't use"
    Show-Group $plan "opt" "Optional: your call"
    $doSafe = $false; $doOpt = "none"
    if ($Debloat) { $doSafe = $true; $doOpt = "all" }
    elseif (-not $interactive) { $doSafe = $true; Write-Host "(No console to ask in: switching off the safe ones only.)" }
    else {
        if ($safe.Count -gt 0) { $doSafe = Ask "Switch off the $($safe.Count) safe ones? [Y/n]" "y" }
        if ($opt.Count -gt 0) {
            $a = Read-Host "Optional ones: switch off all, none, or choose one by one? [a/N/c]"
            if ($a -match '^[aAyY]') { $doOpt = "all" } elseif ($a -match '^[cC]') { $doOpt = "choose" }
        }
    }
    if ($doSafe) { foreach ($i in $safe) { Switch-Off $i; $done++ } }
    foreach ($i in $opt) {
        if ($doOpt -eq "none") { break }
        if ($doOpt -eq "choose" -and -not (Ask "  Switch off $($i.Pkg) ($($i.Desc))? [y/N]" "n")) { continue }
        Switch-Off $i; $done++
    }
    Write-Host "Switched off $done package(s)."
}

if ($AdBlockDns) {
    Write-Host "Turning on AdGuard private DNS..."
    Sh "settings put global private_dns_mode hostname" | Out-Null
    Sh "settings put global private_dns_specifier dns.adguard-dns.com" | Out-Null
}

Write-Host ""
Write-Host "Done. Press Home on the remote. If the TV asks which Home app to use, pick Broom Home -> Always." -ForegroundColor Green
if ($done -gt 0) {
    Write-Host "A restart frees the memory those apps were using."
    if ($interactive -and (Ask "Restart the TV now? (it interrupts anyone watching) [y/N]" "n")) { & $adbExe -s $Tv reboot; Write-Host "Restarting." }
}
Write-Host "Undo everything any time: powershell -ExecutionPolicy Bypass -File .\undo.ps1 -Tv $($Tv -replace ':5555$','')"
