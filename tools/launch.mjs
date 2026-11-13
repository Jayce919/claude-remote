#!/usr/bin/env node
/**
 * 一键启动器：检查环境 → 首次安装依赖 → 启动服务。
 * 用户只需要双击「启动服务.cmd」（Windows）或运行 ./start.sh（macOS / Linux）。
 *
 * 这里集中放中文提示，.cmd 里保持纯 ASCII——cmd 按 ANSI 读文件，
 * 中文注释和括号里的全角符号会被当成命令执行。
 */
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const serverDir = path.join(root, 'server');
const say = (line = '') => console.log(line);

const [major, minor] = process.versions.node.split('.').map(Number);
if (major < 22 || (major === 22 && minor < 5)) {
  say(`✗ 当前 Node 版本是 ${process.version}，需要 22.5 以上。`);
  say('  去 https://nodejs.org/ 下载 LTS 版本，装完重新双击「启动服务.cmd」。');
  process.exit(1);
}
say(`✓ Node ${process.version}`);

/** 找 claude：先看安装器默认位置，再查 PATH */
function findClaude() {
  const isWin = process.platform === 'win32';
  for (const ext of isWin ? ['.exe', '.cmd', ''] : ['']) {
    const candidate = path.join(os.homedir(), '.local', 'bin', `claude${ext}`);
    if (fs.existsSync(candidate)) return candidate;
  }
  const probe = spawnSync(isWin ? 'where' : 'which', ['claude'], { encoding: 'utf8' });
  if (probe.status === 0 && probe.stdout.trim()) {
    return probe.stdout.trim().split(/\r?\n/)[0].trim();
  }
  return null;
}

const claude = findClaude();
if (!claude) {
  say('✗ 没找到 Claude Code（claude 命令）。');
  say('  先在终端里确认这条命令能出结果：');
  say('      claude -p "hi"');
  say('  如果它报错，先把它修好（登录、上游地址、密钥），再回来双击本文件。');
  process.exit(1);
}
say(`✓ Claude Code：${claude}`);

if (!fs.existsSync(path.join(serverDir, 'node_modules'))) {
  say('');
  say('… 首次运行，正在安装依赖（只做一次，需要联网，大约 30 秒）');
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const installed = spawnSync(npm, ['install', '--omit=dev', '--no-audit', '--no-fund'], {
    cwd: serverDir,
    stdio: 'inherit',
    // Windows 上 npm 是 .cmd，必须经由 shell 才能起
    shell: process.platform === 'win32',
  });
  if (installed.status !== 0) {
    say('');
    say('✗ 依赖安装失败。检查网络（尤其是 npm 源）后重新双击本文件。');
    process.exit(1);
  }
}

say('');
say('正在启动服务。这个窗口要一直开着，关掉服务就停了。');
say('');

const child = spawn(process.execPath, ['server.js'], { cwd: serverDir, stdio: 'inherit' });
child.on('exit', (code) => process.exit(code ?? 0));
