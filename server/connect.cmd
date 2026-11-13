@echo off
chcp 65001 >nul
rem 优先用 PATH 里的 tailscale，找不到再用 Windows 默认安装位置
set "TS=tailscale"
where tailscale >nul 2>&1 || set "TS=C:\Program Files\Tailscale\tailscale.exe"

echo === 检查 Tailscale 登录状态 ===
"%TS%" status
echo.

"%TS%" serve --bg 8787
if errorlevel 1 (
  echo 暴露服务失败，请先完成登录。
  pause
  exit /b 1
)

echo.
echo === 已挂到私有网络，可以这样访问 ===
"%TS%" serve status
echo.
for /f "tokens=*" %%i in ('"%TS%" ip -4') do set TAILIP=%%i
echo   手机浏览器打开（首次带令牌，登录一次后就不用带了）：
echo     http://%TAILIP%:8787/?token=^<访问令牌^>
echo   令牌见服务端启动日志，或 config.json 里的 token 字段。
echo.
pause
