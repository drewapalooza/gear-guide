@echo off
setlocal
where javaw.exe >nul 2>nul
if errorlevel 1 (
  echo Java 17 or newer is required. Install it and try again.
  pause
  exit /b 1
)
if not exist "%~dp0build\libs\gear-guide-0.10.0-all.jar" (
  echo Extract the complete Gear Guide package before starting it.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Gear-Guide.ps1"
