@echo off
rem ASCII only: cmd reads .cmd as ANSI, non-ASCII text here breaks parsing.
chcp 65001 >nul
cd /d "%~dp0"

where node >nul 2>&1
if errorlevel 1 (
  echo.
  echo Node.js not found.
  echo Install Node.js 22.5 or newer from https://nodejs.org/ and run this file again.
  echo.
  start "" https://nodejs.org/
  pause
  exit /b 1
)

node "tools\launch.mjs"
echo.
pause
