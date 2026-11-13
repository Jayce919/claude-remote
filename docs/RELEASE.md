# 发版流程

## 1. 改版本号

`android/app/build.gradle.kts`：

```kotlin
versionCode = 25        // 每次 +1，否则手机上覆盖安装会失败
versionName = "1.1.0"
```

服务端版本跟 `server/package.json` 的 `version` 对齐，方便排查问题。

## 2. 构建安卓包

```bash
cd android
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

## 3. 打发行包

```bash
node tools/make-release.mjs
```

会在 `dist/` 下生成 `ClaudeRemote-<版本>/` 和同名 zip。脚本只收源码、启动器、文档和 APK，不会把
`node_modules`、`data/`、`config.json` 打进去——这几个东西带着本机路径和令牌。

## 4. 更新 CHANGELOG.md

在文件顶部加一节，写清楚这一版改了什么、有没有需要用户手动处理的（比如换了包名要卸载重装）。

## 5. 提交并打 tag

```bash
git add -A
git commit -m "release: v1.1.0"
git tag v1.1.0
git push origin main
git push origin v1.1.0
```

## 6. 发布 Release

用 `gh`：

```bash
gh release create v1.1.0 \
  "dist/ClaudeRemote-1.1.0.zip" \
  "dist/ClaudeRemote-1.1.0/server/public/Claude-Remote.apk" \
  --title "v1.1.0" \
  --notes-file <(sed -n '/## 1.1.0/,/^## /p' CHANGELOG.md | head -n -1)
```

或者在网页上手动传：Release 里放两个附件——zip（整包）和 APK（只有客户端）。
README 顶部引用的 `../../releases` 链接会自动指向这里。

## 发布前检查清单

- [ ] `grep -rn "你的姓名\|你的域名\|ts\.net 真实域名\|真实 IP\|sk-\|oc_sk_" .` 无命中
- [ ] 提交作者不是个人邮箱（`git log -1 --format='%ae'`）
- [ ] `dist/` 里没有 `config.json`、`data/`、`node_modules`
- [ ] 新版本能在干净目录里跑通：解压 → 启动 → 手机连上
- [ ] CHANGELOG 写清楚破坏性变更（包名、配置字段）
