# undo.ps1 - put the TV back the way it was: re-enable every package in debloat-list.txt,
# bring back the stock launcher and screensaver, switch off the DNS ad-blocker and remove Broom.
#
#   .\undo.ps1 -Tv 192.168.1.50              # full undo
#   .\undo.ps1 -Tv 192.168.1.50 -KeepBroom   # re-enable packages only, keep Broom installed
param([Parameter(Mandatory = $true)][string]$Tv, [switch]$KeepBroom)
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }
$pkg = "com.broom.launcher"
function Sh([string]$cmd) { (& adb -s $Tv shell $cmd 2>$null | Out-String).Trim() }

& adb connect $Tv | Out-Host
foreach ($line in Get-Content "$PSScriptRoot\debloat-list.txt") {
    $p = ($line -replace "#.*$", "").Trim()
    if ($p) { Sh "pm enable $p" | Out-Null }
}
Sh "settings delete global private_dns_mode" | Out-Null
Sh "settings delete global private_dns_specifier" | Out-Null

if (-not $KeepBroom) {
    Sh "pm enable com.google.android.tvlauncher" | Out-Null
    Sh "pm enable com.google.android.apps.tv.launcherx" | Out-Null       # Google TV home
    Sh "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity" | Out-Null
    Sh "settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop" | Out-Null
    $a11y = Sh "settings get secure enabled_accessibility_services"
    $rest = ($a11y -split ":" | Where-Object { $_ -and $_ -notmatch "com.broom.launcher" }) -join ":"
    if ($rest) { Sh "settings put secure enabled_accessibility_services $rest" | Out-Null }
    else { Sh "settings delete secure enabled_accessibility_services" | Out-Null }
    & adb -s $Tv uninstall $pkg | Out-Host
}
Write-Host "Done. Reboot the TV to finish:  adb -s $Tv reboot" -ForegroundColor Green
