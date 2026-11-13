# Re-register the ClaudeRemote scheduled task.
#
#   - starts at logon,
#   - re-triggers every 10 minutes (was 3 hours) so a crashed server comes back
#     within minutes instead of hours,
#   - runs `node` from PATH (must be Node.js 22.5 or newer).
#
# Usage: right-click -> Run with PowerShell (no admin needed).
# ASCII-only on purpose: PS 5.1 mangles non-ASCII in scripts without a BOM.

$ErrorActionPreference = 'Stop'

$onPath = Get-Command node -ErrorAction SilentlyContinue
if (-not $onPath) {
    throw 'Node.js not found on PATH. Install Node.js 22.5 or newer from https://nodejs.org/.'
}
$version = & $onPath.Source -e "process.stdout.write(process.versions.node)" 2>$null
$parts = "$version".Split('.')
if ([int]$parts[0] -lt 22 -or ([int]$parts[0] -eq 22 -and [int]$parts[1] -lt 5)) {
    throw "Node.js 22.5 or newer is required, found $version."
}
$node = $onPath.Source

$script = Join-Path $PSScriptRoot 'server.js'
$wd     = $PSScriptRoot

if (-not (Test-Path $script)) { throw "server.js not found: $script" }

$action    = New-ScheduledTaskAction -Execute $node -Argument ('"' + $script + '"') -WorkingDirectory $wd
$logon     = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$repeat    = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
               -RepetitionInterval (New-TimeSpan -Minutes 10) `
               -RepetitionDuration (New-TimeSpan -Days 3650)
$settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
               -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName 'ClaudeRemote' -Action $action -Trigger @($logon, $repeat) `
  -Settings $settings -Principal $principal -Force `
  -Description 'Claude Code mobile server: start at logon, health-check every 10 minutes' | Out-Null

Write-Host "OK. Node: $node" -ForegroundColor Green
Write-Host 'ClaudeRemote now has an at-logon trigger plus a 10-minute watchdog.' -ForegroundColor Green
Write-Host 'Remove with:  schtasks /delete /tn ClaudeRemote /f'
Get-ScheduledTask -TaskName 'ClaudeRemote' | Select-Object TaskName, State | Format-List
