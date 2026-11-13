# 手机访问本机 Claude Code

出门没带电脑时，用手机给电脑上的 Claude Code 发消息，等它把活干完再回来看结果。

电脑上的 Claude Code 照常跑，模型、工具、权限都在本机执行。手机只当一个聊天界面。

> **2026-11-09：网页版 PWA 已归档，不再维护。**
> 原前端（`index.html`、`app.js`、`style.css`、`design-tokens.css`、`sw.js`、`vendor/`）不再随仓库发布，`public/` 现在只放安卓安装包和一个静态下载页。
> 后续只维护安卓客户端（`android/`）。服务端接口与 SSE 协议保持不变。

## 它是怎么工作的

```
安卓客户端
   │  Tailscale 私有网络，加密隧道（或局域网明文 HTTP）
   ▼
电脑上的 Node 服务  http://127.0.0.1:8787
   │  Claude Agent SDK
   ▼
Claude Code CLI  →  cc-switch 本地代理 127.0.0.1:15721  →  上游模型
```

三段各自独立：手机只负责显示，Node 服务只负责转发事件，Claude Code 负责干活。

## 前提

- Windows 11 或任意能跑 Node.js 的系统，Node.js 22.5 或更高版本
- 已装 Claude Code CLI，本项目默认路径是 `C:/Users/<你的用户名>/.local/bin/claude.exe`
- 已装 cc-switch，Claude Code 的上游地址写在 `~/.claude/settings.json` 的 `env` 里
- 手机装了 Tailscale，和电脑登录同一个账号

## 启动

```bash
cd server
npm install
npm start
```

启动后终端会打印访问令牌：

```
  Claude Code 手机端服务已启动
  本机访问   http://127.0.0.1:8787/
  访问令牌   <访问令牌>
  手机访问   http://<tailscale-IP>:8787/?token=<访问令牌>
```

令牌写在 `config.json` 里。改端口、模型列表、默认工作目录，都改这个文件。

配置项：

| 键 | 默认值 | 说明 |
|---|---|---|
| `host` | `127.0.0.1` | 监听地址。只给 Tailscale 用就保持默认 |
| `port` | `8787` | 监听端口 |
| `token` | 自动生成 | 访问令牌，首次启动写入 |
| `claudePath` | `C:/Users/<你的用户名>/.local/bin/claude.exe` | Claude Code 可执行文件 |
| `defaultCwd` | `C:/Users/<你的用户名>` | 新对话的默认工作目录 |
| `defaultModel` | `deepseek-v4-pro` | 默认模型 |
| `defaultPermissionMode` | `auto` | 默认权限模式 |
| `models` | 9 项 | 设置面板里的模型候选 |
| `idleTimeoutMs` | 1800000 | 空闲超时 |

想让同一 Wi-Fi 下的手机直接访问，把 `host` 改成 `0.0.0.0`，手机打开 `http://<电脑的局域网IP>:8787/?token=<令牌>`。这个地址只在局域网内有效，出门就用不了，所以日常还是走 Tailscale。

## 配置 Tailscale

电脑端登录并起服务：

```bash
tailscale up --hostname=<你的主机名> --accept-dns=false
```

`--accept-dns=false` 不动系统的 DNS 设置，避免和本机的代理软件冲突。

Windows 上如果 `tailscale up` 没有任何输出，说明流程交给了图形界面。托盘图标里点开 Tailscale 窗口，点里面的「Log in」，浏览器会打开授权页。

登录完成后，把本机服务暴露到私有网络：

```bash
tailscale serve --bg 8787
```

或者直接双击项目里的 `connect.cmd`，它会检查登录状态、挂载服务，并打印手机要访问的地址。

之后用 `https://<你的主机名>.<你的域名>.ts.net/` 访问。带 `?token=` 打开一次，浏览器会存下登录状态，之后直接访问就行。

如果 .ts.net 域名解析不了，改用 tailnet IP：

```bash
tailscale ip -4
```

拿到 `100.x.x.x` 后，手机浏览器打开 `http://100.x.x.x:8787/?token=<访问令牌>`。这条是明文 HTTP，但流量在 WireGuard 隧道里，外网看不到。

手机端：装 Tailscale，登录同一个账号，打开开关，看到 `<你的主机名>` 在线即可。

### 登录一直转圈怎么办

`tailscale up` 没有输出、图形界面点「Log in」也没反应，通常是后台服务连不上 Tailscale 的控制平面。

先看健康状态：

```bash
tailscale status --json
```

如果 `Health` 里是 `register request: Post "https://controlplane.tailscale.com/machine/register": context deadline exceeded`，再查 DNS：

```bash
nslookup controlplane.tailscale.com
```

一个常见根因是 **Clash 的 fake-IP 模式劫持了 DNS**：

```
系统 DNS → 198.18.0.2（Clash）
controlplane.tailscale.com → 198.18.1.226   ← 假地址
```

`tailscaled` 绑定物理网卡直连、不走 TUN，连不到 198.18.x，于是注册请求永远超时。而 `tailscale netcheck` 能正常取 DERPMap，所以看上去「网络是通的、登录就是转圈」。

修法是把域名钉到真实地址。Tailscale 在国内有专用的 anycast 控制平面，真实 DNS 会返回 `192.200.0.101` 这一段。用管理员 PowerShell 加一条 hosts：

```powershell
$h = 'C:\Windows\System32\drivers\etc\hosts'
Copy-Item $h "$env:TEMP\hosts.bak" -Force
Add-Content $h "`r`n192.200.0.101 controlplane.tailscale.com"
ipconfig /flushdns
Restart-Service tailscale
```

之后 `tailscale up` 就会打印授权链接。

回滚：把备份的 hosts 拷回去，再 `ipconfig /flushdns`。

**开通 serve。** 首次 `tailscale serve --bg 8787` 会提示 `Serve is not enabled on your tailnet`，并给一个 `login.tailscale.com/f/serve?node=...` 的链接。在浏览器打开确认一次，再执行同样的命令即可。

**手机端解析不受影响。** 手机上的 Tailscale 用自己的 MagicDNS，能直接解析 `<你的主机名>.<tailnet>.ts.net`。上面那条 hosts 只解决电脑本机 DNS 被 Clash 劫持的问题。

**预防路由冲突。** Clash 全局接管时可能把 tailnet 流量也送进代理节点，表现是设备列表里显示在线但互 ping 不通。在 Clash 规则最前面加一条直连：

```yaml
rules:
  - IP-CIDR,100.64.0.0/10,DIRECT,no-resolve
```

规则必须放在匹配链靠前，否则会被后面的规则先匹配走。

不要用 `netsh winsock reset` 或重装 Tailscale 来处理这个问题。症状在外部网络路径，重置网络栈解决不了，还会破坏其他远程控制软件。

## 在手机上用

当前配置：

```
设备名    <你的主机名>
tailnet   <你的主机名>.<你的域名>.ts.net
tailnet IP <tailnet-IP>
令牌      <访问令牌>
```

1. 手机装 Tailscale，登录同一个账号，打开 VPN 开关
2. 装上安卓客户端（`android/` 构建出的 APK，或你手上已有的安装包）
3. 打开 App，填两样东西：

   ```
   服务地址   https://<你的主机名>.<你的域名>.ts.net
   访问令牌   <访问令牌>（启动服务时终端打印）
   ```

4. 点「连接」进入对话界面

手机浏览器临时用也行：打开 `https://<你的主机名>.<你的域名>.ts.net/?token=<访问令牌>`，带 `?token=` 的链接只需打开一次，之后浏览器会存下登录状态。

界面上的几个地方：

- 左上角图标：会话列表，可以切换和删除对话
- 右上角图标：新建对话
- 标题下面那行字：显示当前工作目录，点它打开设置面板
- 输入框左边的回形针：发图片和文件
- 输入框下面三个小标签：权限模式、当前模型、停止按钮

## 设置项

设置面板在标题下面那行字里。

**模型** 填的是上游能识别的模型名。默认值 `deepseek-v4-pro`，和电脑端当前用的一致。可选值见 `config.json` 的 `models` 数组。

**思考强度** 对应 CLI 的 `--effort`，可填 `low`、`medium`、`high`、`xhigh`、`max`。留空表示跟随上游默认。

**权限模式** 和电脑端同名：

| 模式 | 含义 |
|---|---|
| auto | Claude 自己判断该不该做 |
| default | 每一步都问你，手机上会弹出「允许 / 拒绝」 |
| acceptEdits | 自动接受文件修改，其他操作仍然问 |
| plan | 只读，不动文件 |
| dontAsk | 不问，一律拒绝 |

**工作目录** 决定 Claude Code 在哪个目录干活。填错了会新开一个会话，上下文不接着。

## 和桌面端同步对话

手机端和桌面端跑的是同一套 Claude Code 会话文件，都存在 `~/.claude/projects/<工作目录编码>/<会话 ID>.jsonl`。所以手机上的对话在电脑上能直接续接：

```bash
claude --resume <会话 ID>
```

不带 ID 直接跑 `claude --resume`，会列出所有会话，手机端的也在里面。想让它显示正式标题而不是第一句原文，用 SDK 的 `renameSession()` 写一条 `custom-title` 记录——`lib/desktopSync.js` 里的 `syncSessionTitle()` 就是干这个的，服务每次生成标题时都会调它。

已有会话可以一次性回填标题、导出一份 Markdown 存档：

```bash
node sync-desktop.mjs --export
```

- 标题来源：`data/titles.json` 里的手工标题优先，否则用对话自己的标题
- 导出目录默认 `data/exports/`
- 手工改过标题的会话，改完再跑一次即可

**注意：** Claude Desktop 应用侧边栏只列它自己开的会话——它有自己的会话索引，还要跟 claude.ai 账号同步。手机端的会话进不了那个列表，别指望在应用里直接点开，要在终端里 `claude --resume`，或者用 `node sync-desktop.mjs --export` 导出的 Markdown。

## 已知限制

**电脑不能休眠。** 电脑睡了就收不到消息。合上盖子照样会睡，Windows 的「从不睡眠」只管接通电源的情况。

**不要在 Claude Desktop 的终端里启动服务。** 那个终端里 `ANTHROPIC_BASE_URL` 已经被设成 `http://127.0.0.1:15721/claude-desktop`，是 cc-switch 给桌面端的专用路由。子进程用了它会返回 401。`applyUpstreamEnv()` 现在会用 `~/.claude/settings.json` 里的值强制覆盖继承来的环境变量，所以正常启动不受影响。如果你自己写脚本起服务，记得不要预先导出这个变量。

**图片要看模型。** 协议层面图片已经能传过去了，但上游模型得支持视觉。换 `deepseek-v4-flash-vision-exp` 这类 vision 模型才能看懂图片，`deepseek-v4-pro` 看不懂。

**选框是模型主动问才有。** Claude Code 调用 AskUserQuestion 工具时，手机上会出现选项卡片。普通的工具调用显示成一张可展开的卡片。

**切模型会新开一轮。** 模型、思考强度、权限模式可以在会话中途改，改完下一轮生效。

**上游换了要重启服务。** 在 cc-switch 里切换供应商后，服务要重启才会读到新的 `~/.claude/settings.json`。

**本机浏览器打不开 ts.net 域名。** Clash 的 fake-IP 会把 `<你的主机名>.<你的域名>.ts.net` 解析成 `198.18.x`。手机不受影响（走自己的 MagicDNS）。要在电脑上自测，用 `curl --resolve` 指定真实 IP：

```bash
curl --noproxy '*' --resolve "<你的主机名>.<你的域名>.ts.net:443:<tailnet-IP>" https://<你的主机名>.<你的域名>.ts.net/
```

## 供应商跟随（/api/models）

`GET /api/models` 返回当前供应商与可选模型，每次调用都实时读，不缓存：

```json
{
  "provider": "DeepSeek",
  "providerId": "<provider-ID>",
  "mode": "proxy",
  "upstream": "http://127.0.0.1:15721",
  "activeModel": "",
  "models": ["deepseek-v4-pro", "deepseek-flash", "..."],
  "visionModels": ["deepseek-v4-flash-vision-exp"],
  "revision": "<provider-ID>:<时间戳>:<时间戳>"
}
```

判定当前路由的依据是 `~/.cc-switch/live-state.json` 的 `apps.claude.proxy_route`，**不是** `providers.is_current`。实测这两者会不一致：`proxy_route` 可能已经指向新供应商，`is_current` 还停在旧的那个，而代理日志里最近的请求走的是新供应商。

候选模型由三部分拼成：provider 配置里的 `*MODEL*` 值、`proxy_request_logs` 近 7 天成功过的模型、`config.json` 的 `models` 兜底。cc-switch 没有为 Claude Code 这一侧维护模型清单，`providers.meta` 里只有 `claude-desktop` 的行带 `claudeDesktopModelRoutes`。

这个接口用 `node:sqlite` 读库，需要 **Node 22.5 以上**。用更旧的 Node 起服务不会报错，只是读不到数据库、只返回兜底列表。

换供应商后不必重启本服务：`Conversation.ensureQuery()` 在建会话前会检查 `settings.json` 的 mtime，变了就重新应用环境变量。

## 界面设计

样式表用的是 Claude 自己那套设计系统（CDS），不是照着截图描的。令牌逆向自 Claude Desktop 里的 claude.ai 渲染层。

安卓端把同一套令牌放在 `android/app/src/main/java/com/clauderemote/app/ui/theme/`（`CdsColors.kt`、`CdsDimens.kt`、`CdsMotion.kt`）。

几个关键值：

| 用途 | 浅色 | 深色 |
|---|---|---|
| 页面背景 | `#f9f9f7` | `#0b0b0b` |
| 次级表面 | `#fcfcfb` | `#151515` |
| 正文 | `#29261b` | `#f5f4ef` |
| 次要文字 | `#52514e` | `#c3c2b7` |
| 弱化文字 | `#898781` | `#898781` |
| 品牌橙 | `#d97757` | `#d97757` |
| 强调色 | `#c6613f` | `#d97757` |
| 主按钮 | `#0b0b0b` | `#ffffff` |
| 用户气泡 | 灰色 10% 透明 | 白色 5% 透明 |

正文色用的是官方手机版的值 `#29261b` / `#f5f4ef`，比网页端的 `#0b0b0b` / `#f0efec` 暖一档。强调色也按手机版的做法随主题换档：浅色用深一点的 `#c6613f`，深色用 `#d97757`。

正文 13px、行高 19px，聊天正文 14px。消息块间距 4px，气泡宽度 85%，圆角 10px，输入框圆角 14px。

品牌橙只用在图标和强调处。主按钮在浅色下是近黑、深色下是纯白，和 claude.ai 一致。

字体用官方同款回退栈（system-ui → PingFang SC / 微软雅黑），没有内嵌 Anthropic 的 woff2 文件。

公式用 KaTeX 渲染。行内写 `$...$`，行间写 `$$...$$` 或 `\[...\]`。官方手机版也内嵌了 `latinmodern-math.otf`，支持 LaTeX。

## 目录结构

```
server\
├─ server.js              HTTP 服务、鉴权、SSE 推送
├─ package.json           npm start / npm test
├─ config.example.json    配置模板，复制成 config.json 后改
├─ lib\
│  ├─ config.js           读配置，注入 cc-switch 的上游地址
│  ├─ conversation.js     一个对话对应一个常驻的 Claude Code 会话
│  ├─ desktopSync.js      把会话标题同步给桌面端（SDK 的 renameSession）
│  ├─ manager.js          对话增删、事件广播、落盘
│  └─ store.js            对话历史的 JSON 持久化
├─ public\
│  └─ index.html          安卓安装包下载页
├─ data\conversations.json 对话历史（运行时生成）
├─ data\titles.json        手工标题，按会话 ID 对应
├─ data\exports\           导出的 Markdown 对话存档
├─ uploads\                手机上传的文件
├─ connect.cmd             挂载 Tailscale 并打印手机访问地址
├─ start.cmd               首次安装依赖并启动
├─ 重启服务.cmd            杀掉 8787 端口上的进程再拉起
├─ set-watchdog.ps1        注册登录自启 + 10 分钟巡检的计划任务
├─ sync-desktop.mjs        回填桌面端标题、导出 Markdown、打印续接表
├─ test-e2e.mjs           端到端测试：选框问答
├─ test-permission.mjs    端到端测试：权限确认
└─ test-image.mjs         端到端测试：图片上传
```

## 开机自启

出门在外时电脑如果重启过，服务就没了。让它开机自动跑起来：

```bash
schtasks /create /tn "ClaudeRemote" /tr "\"C:\Program Files\nodejs\node.exe\" \"<server 目录的绝对路径>\server.js\"" /sc onlogon /rl limited /f
```

更省事的做法是右键运行 `set-watchdog.ps1`，它会从 PATH 找到 `node`，注册登录自启加 10 分钟巡检（不需要管理员权限）。

取消：

```bash
schtasks /delete /tn "ClaudeRemote" /f
```

也可以把 `start.cmd` 的快捷方式丢进 `shell:startup` 打开的启动文件夹，效果一样，删起来更直观。

注意 Node 路径要写对：`node` 必须在 PATH 里，且版本 ≥ 22.5。

## 安全

这个服务能执行命令，等于把电脑的 shell 暴露给手机。几层限制：

- 只监听 `127.0.0.1`，外网访问不到
- Tailscale 私有网络，只有你自己的设备能连
- 每次请求校验令牌，令牌是 32 位随机字符串
- 不会用 `bypassPermissions`，需要授权的操作都会在手机上问

### tailnet 身份免令牌

经 `tailscale serve` 进来的请求，tailscaled 会附带 `Tailscale-User-Login`（外部伪造的同名头会被剥掉）。把这个登录名写进 `config.json`：

```json
"allowedLogins": ["你的 Tailscale 登录邮箱"]
```

之后从你自己的 tailnet 设备打开 `https://<主机>.<tailnet>.ts.net/` 就直接进去，不用带 `?token=`；令牌方式仍然有效，本地直连 `127.0.0.1:8787` 不受影响。留空数组即关闭该功能。第一次用到某个登录名时，服务端会打印一行 `[auth] 放行/拒绝 tailnet 身份：…` 方便核对。

想更严一点，就把权限模式设成 `default` 或 `dontAsk`。

## 测试

```bash
node test-e2e.mjs <访问令牌>        # 选框问答，跑通整条链路
node test-permission.mjs <访问令牌>  # 权限确认，放行后命令真的执行
node test-image.mjs <访问令牌>       # 图片上传，vision 模型能看懂
node test-resume.mjs <访问令牌>      # 服务重启后能否续上原会话
node test-tailnet.mjs <地址> <访问令牌>  # 走 Tailscale HTTPS 的全链路
```

前四个是本地端到端测试：走 HTTP 接口收事件，模拟在手机上点选框或点「允许」。

`test-tailnet.mjs` 走真实的 `https://<你的主机名>.<你的域名>.ts.net`。脚本里给 `dns.lookup` 打了补丁，把域名钉到 tailnet IP——用于电脑 DNS 被代理软件劫持、手机没这个问题的情况。

```bash
node test-tailnet.mjs https://<你的主机名>.<你的域名>.ts.net <访问令牌>
```
