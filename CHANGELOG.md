# 更新日志

## 1.0.0

首个公开版本。

### 服务端

- Node.js 22.5+，HTTP + SSE，令牌鉴权；可挂 `tailscale serve` 走 HTTPS，也可局域网直连
- 会话级通道切换：cc-switch 里存在对应供应商时，手机端可以选直连哪条上游
- 端口被占用时自动顺延，并在启动日志里说明；只列出手机真正能用的地址
- 可选跟随 cc-switch 读取模型列表与用量；没装 cc-switch 也能跑

### 安卓客户端

- Kotlin + Jetpack Compose，原生渲染 Markdown、代码块、表格，公式走 KaTeX
- 多选 / 单选交互卡片、权限审批、图片附件、队列发送
- 浅色与深色两套 Claude 设计系统令牌，字号可缩放
- 无障碍：48dp 触控目标、读屏语义、减动效跟随系统开关
- 自带 Gradle wrapper，`./gradlew assembleDebug` 即可构建

### 发行包

- 服务端 + 启动器 + 教程 + APK 收进一个 zip，解压双击即可运行
- `快速开始.html` 提供四步上手与故障排查
- APK 同时放在服务端首页，手机连上就能下载安装
