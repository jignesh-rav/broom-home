# install.ps1 - put Broom Home on a Mi TV over Wi-Fi ADB and give it the permissions it needs.
# Safe to run again any time (for example after a Xiaomi update resets something).
#
#   .\install.ps1 -Tv 192.168.1.50                 # install + set up Broom
#   .\install.ps1 -Tv 192.168.1.50 -Debloat        # also disable the packages in debloat-list.txt
#   .\install.ps1 -Tv 192.168.1.50 -AdBlockDns     # also turn on AdGuard private DNS (blocks ads system-wide)
#   .\install.ps1 -Tv 192.168.1.50 -Apk path\to\BroomHome.apk
param(
    [Parameter(Mandatory = $true)][string]$Tv,
    [string]$Apk,
    [switch]$Debloat,
    [switch]$AdBlockDns
)
$ErrorActionPreference = "Continue"
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }
$root = Split-Path $PSScriptRoot -Parent
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
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb not found. Install it with:  winget install Google.PlatformTools   then open a new PowerShell window." -ForegroundColor Red
    exit 1
}

$pkg = "com.broom.launcher"
function Adb { & adb -s $Tv @args }
function Sh([string]$cmd) { (& adb -s $Tv shell $cmd 2>$null | Out-String).Trim() }

Write-Host "Connecting to $Tv (accept the 'Allow USB debugging' prompt on the TV if it appears)..."
& adb connect $Tv | Out-Host
if ((& adb -s $Tv get-state 2>$null) -ne "device") {
    Write-Host "Could not connect. Check the IP, that the TV is on, and that ADB debugging is enabled (see docs/SETUP.md)." -ForegroundColor Red
    exit 1
}

Write-Host "Installing $Apk ..."
Adb install -r $Apk | Out-Host

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
Sh "pm disable-user --user 0 com.google.android.tvlauncher" | Out-Null      # stock launcher; undo.ps1 brings it back

if ($Debloat) {
    Write-Host "Disabling packages from debloat-list.txt..."
    $installed = Sh "pm list packages"
    foreach ($line in Get-Content "$PSScriptRoot\debloat-list.txt") {
        $p = ($line -replace "#.*$", "").Trim()
        if (-not $p) { continue }
        if ($installed -notmatch [regex]::Escape("package:$p")) { continue }
        Write-Host "  disable $p"
        Sh "pm disable-user --user 0 $p" | Out-Null
    }
}

if ($AdBlockDns) {
    Write-Host "Turning on AdGuard private DNS..."
    Sh "settings put global private_dns_mode hostname" | Out-Null
    Sh "settings put global private_dns_specifier dns.adguard-dns.com" | Out-Null
}

Write-Host ""
Write-Host "Done. Press Home on the remote. If the TV asks which Home app to use, pick Broom Home -> Always." -ForegroundColor Green
Write-Host "Undo everything any time: .\undo.ps1 -Tv $($Tv -replace ':5555$','')"
