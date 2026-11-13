#!/bin/sh
# 一键启动（macOS / Linux）：检查环境、装依赖、起服务，逻辑在 tools/launch.mjs 里。
cd "$(dirname "$0")" || exit 1

if ! command -v node >/dev/null 2>&1; then
  echo "没找到 Node.js。请先安装 22.5 以上版本：https://nodejs.org/"
  exit 1
fi

exec node tools/launch.mjs
