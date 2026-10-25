$ErrorActionPreference = 'Stop'
$jarPath = Join-Path $PSScriptRoot 'build\libs\gear-guide-0.11.0-all.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build first with .\gradlew.bat test shadowJar, or extract the full release folder.' }
$javaCommand = Get-Command javaw.exe -ErrorAction SilentlyContinue
if (-not $javaCommand) { throw 'Install Java 17 or newer, then run this launcher again.' }
# Each launch gets an immutable copy: rebuilding build/libs must never alter a running JVM's archive.
$sessionDirectory = Join-Path $PSScriptRoot ('runtime\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $sessionDirectory -Force | Out-Null
$sessionJar = Join-Path $sessionDirectory 'gear-guide-all.jar'
Copy-Item -LiteralPath $jarPath -Destination $sessionJar
Start-Process -FilePath $javaCommand.Source -ArgumentList @('-ea', '-jar', ('"' + $sessionJar + '"'), '--developer-mode') -WindowStyle Hidden
