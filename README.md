# Claude Remote

用手机远程驱动电脑上的 Claude Code：电脑上跑一个 Node 服务，手机装上配套的安卓客户端，随时发消息、看流式输出、点权限确认。

## 最快上手（推荐）

到 [Releases](../../releases) 下载 `ClaudeRemote-*.zip`，解压后：

1. 电脑上双击 **启动服务.cmd**（macOS / Linux 运行 `./start.sh`）。第一次会花半分钟装依赖，之后窗口里会显示「手机访问」地址和「访问令牌」。
2. 手机连上同一个 Wi-Fi，浏览器打开那个地址，下载并安装页面上的 **Claude-Remote.apk**。
3. 打开 App，填上刚才的地址和令牌，点连接。

包里的 **快速开始.html** 有带截图的详细说明和故障排查。

> 前提只有一个：电脑上的 `claude -p "hi"` 能正常出结果。用第三方中转也一样，本工具不关心你的上游是谁。

## 它解决什么问题

Claude Code 需要在有项目文件、有工具链的电脑上跑。人离开电脑之后，任务就停在那里了。Claude Remote 把电脑上的 Claude Code 会话接到手机上：手机只负责显示和输入，命令执行、文件读写、模型调用全在电脑上进行。电脑不休眠，任务就能一直跑。

## 前置条件

- Node.js 22.5 或更高版本（`node:sqlite` 从这一版开始可用）
- 电脑上已经能跑 `claude`（Claude Code CLI），并且完成过登录
- 如果用的是第三方中转或本地代理，确保 `claude` 能正常出结果即可，本项目不限定上游
- 可选：Tailscale。要在外网访问就必须有，只在家里 Wi-Fi 用可以不要
- 可选：cc-switch。装了它，模型列表和用量面板能自动跟随当前供应商；没装也能跑，只是模型名要在 `config.json` 里手写

## 从源码运行（开发者）

```bash
cd server
npm install
npm start
```

首次启动会做三件事：

1. 生成 `server/config.json`（包含端口、默认工作目录等）
2. 生成一个随机访问令牌
3. 在终端打印本机地址和令牌：

```
  Claude Code 手机端服务已启动
  本机访问   http://127.0.0.1:8787/
  访问令牌   <一串随机字符>
  手机访问   http://<tailscale-IP>:8787/?token=<访问令牌>
```

浏览器打开 `http://127.0.0.1:8787/?token=<令牌>` 会看到一个静态页面，能打开就说明服务在跑。想确认接口是否正常：

```bash
curl -H "Authorization: Bearer <令牌>" http://127.0.0.1:8787/api/bootstrap
```

令牌也会写进 `config.json` 的 `token` 字段，想换随时改。

## 手机端怎么连

1. 构建或获取 APK

   ```bash
   cd android
   ./gradlew assembleDebug          # Windows 用 gradlew.bat assembleDebug
   ```

   需要 JDK 17 以上，Gradle 由仓库自带的 wrapper 负责下载，不用另外装。产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。如果你手上已有构建好的 APK，直接装那个也行；也可以把它放进 `server/public/`，从服务的首页下载。

2. 装到手机，打开 App
3. 填两样东西：
   - 服务地址：Tailscale 场景填 `https://<你的设备名>.<你的tailnet>.ts.net`；局域网场景填 `http://<电脑的局域网IP>:8787`
   - 访问令牌：服务端启动时打印的那串字符
4. 点「连接」

如果走局域网明文 HTTP，需要先把你电脑的地址加进 `android/app/src/main/res/xml/network_security_config.xml` 的白名单，否则安卓会拦截明文请求。

## 可选：把服务暴露到外网

### Tailscale（推荐）

电脑和手机登录同一个 tailnet，然后在电脑上：

```bash
tailscale serve --bg 8787
```

之后手机访问 `https://<设备名>.<tailnet>.ts.net/`。`connect.cmd` 把这几步包好了，双击即可。

Tailscale 的 MagicDNS 名失效时，可以在 `config.json` 里填 `altHost`（填 tailnet IP），服务会在同一端口再监听一份只对 tailnet 可见的入口。

### systemd（Linux）

```ini
[Unit]
Description=Claude Remote
After=network.target

[Service]
WorkingDirectory=/path/to/claude-remote/server
ExecStart=/usr/bin/node server.js
Restart=on-failure

[Install]
WantedBy=default.target
```

存成 `~/.config/systemd/user/claude-remote.service`，然后 `systemctl --user enable --now claude-remote`。

### Windows 计划任务

右键运行 `server/set-watchdog.ps1`（不需要管理员权限）。它会注册一个登录自启、每 10 分钟巡检一次的计划任务，进程掉了会被重新拉起。卸载用：

```bash
schtasks /delete /tn ClaudeRemote /f
```

## 常见问题

**401 未授权**

请求里没带对令牌。检查 App 里填的是不是服务端当前 `config.json` 里的 `token`；改过 `config.json` 后要重启服务。

**402 或上游报余额/额度错误**

这是模型上游的问题，不是服务本身。到 Claude Code 或中转服务商那边确认额度与密钥。

**手机连不上**

- 先确认电脑上的服务在跑：浏览器打开 `http://127.0.0.1:8787/?token=<令牌>`
- 服务默认只监听 `127.0.0.1`。局域网直连要把 `host` 改成 `0.0.0.0`，并放行防火墙端口
- Tailscale 场景确认两端都登录了同一个 tailnet，`tailscale status` 能看到对方在线
- 电脑休眠后连接会断，检查电源设置

**模型列表是空的 / 只有兜底列表**

- 确认 `node -v` ≥ 22.5
- 没装 cc-switch 时属正常，服务会退回 `config.json` 里 `models` 数组
- 装了 cc-switch 但刚切过供应商：重启一次服务

**手机上中文/公式显示异常**

公式走 KaTeX，资源已经打进 APK。如果只有公式不对，先确认安装的是完整包而不是被裁剪过的构建产物。

## 目录说明

```
claude-remote-public/
├─ server/                Node 服务端
│  ├─ server.js           HTTP、鉴权、SSE
│  ├─ lib/                配置、会话、持久化、桌面端同步
│  ├─ public/             静态下载页
│  ├─ test/               单元与端到端脚本
│  └─ *.cmd / *.ps1       Windows 启停与自启脚本
├─ android/               Kotlin + Jetpack Compose 客户端
│  ├─ app/src/main/java/com/clauderemote/app/
│  └─ docs/               测试方案
└─ README.md
```

## 安全提醒

这个服务能执行命令，等于把电脑的 shell 交给手机。默认只监听回环地址、所有请求都要令牌；经 `tailscale serve` 访问时还可以配置 `allowedLogins` 用 tailnet 身份免令牌。不要把端口直接暴露到公网。
