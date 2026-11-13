# claude-remote 项目说明

手机远程访问 Claude Code 的自建服务。Node 后端（`server.js` + `lib/`），安卓原生客户端在 `../android`。

## 命令

- 启动：`node` 版本需 ≥ 22.5（`/api/models` 用 `node:sqlite` 读 cc-switch 的数据库）。手动启动：
  `node server.js`，cwd 必须是工程根；Windows 上也可以双击 `start.cmd`。
- 重启：跑 `重启服务.cmd`（先杀 8787 端口再拉起）。**服务在跑时别重启，会断掉正在连接的手机。**
- 语法检查（无构建步骤）：`npm test`
- 在线冒烟（服务在跑时，只读）：`npm run smoke`

## 约定

- 上游是 cc-switch 的 `/claude-desktop` 代理，模型列表实时读 `~/.cc-switch/live-state.json` 和 `cc-switch.db`，不要写死模型名。
- 派生 CLI 前必须显式注入 `ANTHROPIC_BASE_URL` / `ANTHROPIC_AUTH_TOKEN`，并清掉宿主注入的 `CLAUDE_CODE_*` 变量（见 `lib/config.js` 的 `applyUpstreamEnv`）。
- 数据：`data/conversations.json` 是全部对话，`uploads/` 是上传图片。改动这些要小心，别误删用户数据。

## 坑

- `start.cmd`、`重启服务.cmd`、`set-watchdog.ps1` 必须保持纯 ASCII：cmd 按 ANSI 读 .cmd，中文注释会破坏解析。
- 如果本机 DNS 被代理软件（fake-IP 模式）劫持，自测 ts.net 域名要 `curl --noproxy '*' --resolve <你的主机名>.<你的域名>.ts.net:443:<tailnet-IP> https://<你的主机名>.<你的域名>.ts.net/`，直接 curl 域名会失败。
- 重启服务时 `Stop-ScheduledTask` 杀不掉里面的 node 子进程，要 `netstat -ano | findstr :8787` 找 PID 再 `taskkill /F`。
- 服务崩溃的真因曾是未处理的 Promise 拒绝（客户端断连）。`server.js` 里 `handleRequest().catch()` 和 `process.on('unhandledRejection')` 这层兜底别拆。

## 网络入口

- HTTPS（默认）：`tailscale serve --bg 8787` → `https://<设备名>.<tailnet>.ts.net/`
- 可选备用直连：`config.json` 里填 `"altHost": "<tailnet-IP>"`（或局域网 IP），服务会在同一端口再监听一份，
  只对该地址可达。需要的话，客户端可以据此实现「主域名解析失败时自动切换」。
  用明文的地址要同步加进安卓端的 `network_security_config.xml` 白名单。
- `tailscale serve --http=8080` 那条是早期试验，按 IP 访问会 404（Serve 按 Host 匹配），
  不要再用；要去掉执行 `tailscale serve --http=8080 off`。

## 验证

- 改完服务端代码：先 `npm test` 过语法，等下次重启生效（计划任务 `ClaudeRemote` 每 10 分钟巡检兜底，进程掉线后自动拉起；改了巡检规则要跑一次 `set-watchdog.ps1` 重新注册）。
- 端到端脚本在根目录：`node test-e2e.mjs` 等，按需跑。
