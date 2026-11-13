@echo off
rem Restart the claude-remote server: kill whatever holds port 8787, then start fresh.
rem Keep this file ASCII-only -- cmd reads .cmd as ANSI, Chinese text here breaks parsing.
rem The folder path is resolved from this script's own location, so no non-ASCII literal.
cd /d "%~dp0"

rem /api/models reads the cc-switch SQLite database via node:sqlite, which needs Node >= 22.5.
rem Only `node` from PATH is used; install Node 22.5 or newer if the check below fails.
set "NODE=node"
node -e "const v=process.versions.node.split('.').map(Number); process.exit(v[0]>22||(v[0]===22&&v[1]>=5)?0:1)" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js 22.5 or newer is required.
  echo Install it from https://nodejs.org/ and make sure `node` is on PATH.
  pause
  exit /b 1
)

echo [1/3] stopping the process on port 8787 ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8787" ^| findstr LISTENING') do (
  echo       kill PID %%p
  taskkill /F /PID %%p >nul 2>&1
)
timeout /t 2 >nul

echo [2/3] starting server ...
start "claude-remote" "%NODE%" server.js
timeout /t 4 >nul

echo [3/3] checking ...
netstat -ano | findstr ":8787" | findstr LISTENING
if errorlevel 1 (
  echo [FAIL] not listening - check the new console window for errors
) else (
  echo [OK] running at http://127.0.0.1:8787/
)
pause
