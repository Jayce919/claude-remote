#!/usr/bin/env node
/**
 * 打发行包：把服务端、启动器、教程和 APK 收进一个目录并压成 zip。
 *
 * 用法：
 *   node tools/make-release.mjs
 *
 * 前提：安卓端已经构建过一次（android/app/build/outputs/apk/debug/app-debug.apk 存在）。
 * 产物：dist/ClaudeRemote-<版本>.zip
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(root, 'dist');

const gradle = fs.readFileSync(path.join(root, 'android/app/build.gradle.kts'), 'utf8');
const version = (gradle.match(/versionName\s*=\s*"([^"]+)"/) || [])[1];
if (!version) {
  console.error('读不到 android/app/build.gradle.kts 里的 versionName');
  process.exit(1);
}

const apk = path.join(root, 'android/app/build/outputs/apk/debug/app-debug.apk');
if (!fs.existsSync(apk)) {
  console.error('没找到构建产物：先跑一次 cd android && ./gradlew assembleDebug');
  process.exit(1);
}

const name = `ClaudeRemote-${version}`;
const out = path.join(dist, name);
fs.mkdirSync(out, { recursive: true });

// 服务端：不带运行时数据、依赖和本地配置
const skip = new Set(['node_modules', 'data', 'uploads', 'config.json']);
fs.cpSync(path.join(root, 'server'), path.join(out, 'server'), {
  recursive: true,
  filter: (src) => {
    const base = path.basename(src);
    if (skip.has(base)) return false;
    if (base.endsWith('.log')) return false;
    return true;
  },
});

// 启动器与文档
fs.cpSync(path.join(root, 'tools'), path.join(out, 'tools'), { recursive: true });
for (const file of ['README.md', 'README.en.md', 'LICENSE', '快速开始.html', '启动服务.cmd', 'start.sh']) {
  const from = path.join(root, file);
  if (fs.existsSync(from)) fs.copyFileSync(from, path.join(out, file));
}
// README 里引用了截图，一起带上，解压后链接不会断
fs.cpSync(path.join(root, 'docs/images'), path.join(out, 'docs/images'), { recursive: true });

// 安装包放进服务端的静态目录，手机连上就能下载
fs.copyFileSync(apk, path.join(out, 'server/public/Claude-Remote.apk'));

const zipPath = path.join(dist, `${name}.zip`);
// 传相对路径：Git Bash 里的 tar 会把 "D:\..." 的冒号当成远程主机
const rel = (p) => path.relative(root, p).replace(/\\/g, '/');
const cmd =
  process.platform === 'win32'
    ? { file: 'tar', args: ['-a', '-c', '-f', rel(zipPath), '--exclude=*/server/node_modules', '-C', 'dist', name], cwd: root }
    : { file: 'zip', args: ['-qr', zipPath, name, '-x', `${name}/server/node_modules/*`], cwd: dist };

const zipped = spawnSync(cmd.file, cmd.args, { encoding: 'utf8', cwd: cmd.cwd || root });
if (zipped.status !== 0) {
  console.error(`打包失败：${zipped.stderr || zipped.error?.message || ''}`);
  console.error('macOS / Linux 需要系统里有 zip 命令。');
  process.exit(1);
}

const mb = (fs.statSync(zipPath).size / 1048576).toFixed(1);
console.log(`✓ ${path.relative(root, zipPath)}  ${mb} MB`);
