@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist node_modules (
  echo First run - installing dependencies ...
  call npm install
)
rem /api/models reads the cc-switch SQLite database via node:sqlite, which needs Node >= 22.5.
rem Only `node` from PATH is used; install Node 22.5 or newer if the check below fails.
node -e "const v=process.versions.node.split('.').map(Number); process.exit(v[0]>22||(v[0]===22&&v[1]>=5)?0:1)" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js 22.5 or newer is required.
  echo Install it from https://nodejs.org/ and make sure `node` is on PATH.
  pause
  exit /b 1
)
node server.js
pause
