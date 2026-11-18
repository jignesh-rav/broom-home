# undo.ps1 - put the TV back the way it was: turn on every package in debloat-list.txt and every
# debloat-<brand>.txt, bring back the stock launcher and screensaver and the TV's own animation and key-repeat
# settings, switch off the DNS ad-blocker and remove Broom.
#
#   .\undo.ps1 -Tv 192.168.1.50              # full undo
#   .\undo.ps1 -Tv 192.168.1.50 -KeepBroom   # turn the debloated packages back on only, keep Broom installed
param([Parameter(Mandatory = $true)][string]$Tv, [switch]$KeepBroom)
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }
$pkg = "com.broom.launcher"
$adbExe = (Get-Command adb -CommandType Application | Select-Object -First 1).Source
function Sh([string]$cmd) { (& $adbExe -s $Tv shell $cmd 2>$null | Out-String).Trim() }

& $adbExe connect $Tv | Out-Host
Write-Host "Turning the debloated packages back on..."
foreach ($f in Get-ChildItem "$PSScriptRoot\debloat-*.txt") {
    foreach ($line in Get-Content $f.FullName) {
        $p = ($line -replace "#.*$", "").Trim()
        if (-not $p) { continue }
        Sh "cmd package install-existing $p" | Out-Null   # brings back a package removed for the user ("remove-for-user")
        Sh "pm enable $p" | Out-Null
    }
}
# the animation and key-repeat values the installer saved before changing them
$before = Sh "cat /data/local/tmp/broom-speed-before.txt"
if ($before -and $before -notmatch 'No such file') {
    $v = $before -split '\s+'
    $keys = @('global window_animation_scale', 'global transition_animation_scale', 'global animator_duration_scale', 'secure long_press_timeout')
    for ($i = 0; $i -lt 4; $i++) {
        if (-not $v[$i] -or $v[$i] -eq 'null') { Sh "settings delete $($keys[$i])" | Out-Null } else { Sh "settings put $($keys[$i]) $($v[$i])" | Out-Null }
    }
    Sh "rm /data/local/tmp/broom-speed-before.txt" | Out-Null
}
Sh "settings delete global private_dns_mode" | Out-Null
Sh "settings delete global private_dns_specifier" | Out-Null

if (-not $KeepBroom) {
    Sh "cmd package install-existing com.google.android.tvlauncher" | Out-Null   # removed for the user on TCL
    Sh "pm enable com.google.android.tvlauncher" | Out-Null
    Sh "pm enable com.google.android.apps.tv.launcherx" | Out-Null       # Google TV home
    Sh "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity" | Out-Null
    Sh "settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop" | Out-Null
    $a11y = Sh "settings get secure enabled_accessibility_services"
    $rest = ($a11y -split ":" | Where-Object { $_ -and $_ -notmatch "com.broom.launcher" -and $_ -ne "null" }) -join ":"
    if ($rest) { Sh "settings put secure enabled_accessibility_services $rest" | Out-Null }
    else { Sh "settings delete secure enabled_accessibility_services" | Out-Null }
    & $adbExe -s $Tv uninstall $pkg | Out-Host
}
Write-Host "Done. Reboot the TV to finish:  adb -s $Tv reboot" -ForegroundColor Green
