// 把手机端对话同步到桌面端：给每个会话写一份 custom-title.json，
// 让桌面端 Claude Code 能认出这些会话的正式标题；顺便导出一份 Markdown 存档。
//
//   node sync-desktop.mjs                 只写标题并打印续接表
//   node sync-desktop.mjs --export        同时把对话导出成 Markdown（默认 data/exports）
//   node sync-desktop.mjs --export <输出目录>
//
// 标题来源：data/titles.json 里的手工标题优先，否则用对话自己的标题。
// 手工标题是给早期会话用的——那时候标题还直接取第一句原文，读起来像日志。

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { syncSessionTitle, findSessionDir } from './lib/desktopSync.js';
import { applyUpstreamEnv } from './lib/config.js';

const root = path.dirname(fileURLToPath(import.meta.url));
applyUpstreamEnv();
const dataFile = path.join(root, 'data', 'conversations.json');
const titlesFile = path.join(root, 'data', 'titles.json');

const args = process.argv.slice(2);
const exportIndex = args.indexOf('--export');
const exportDir = exportIndex >= 0 ? args[exportIndex + 1] || path.join(root, 'data', 'exports') : null;

function loadJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

const raw = loadJson(dataFile, []);
const conversations = Array.isArray(raw) ? raw : raw.conversations || [];
const overrides = loadJson(titlesFile, {});

function formatTime(ms) {
  if (!ms) return '-';
  const d = new Date(Number(ms));
  const pad = (n) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function messageToMarkdown(message) {
  if (message.role === 'user') {
    const text = String(message.text || '').trim();
    return `### 用户 · ${formatTime(message.ts)}\n\n${text || '（无文字）'}`;
  }
  const blocks = message.blocks || [];
  const parts = [];
  for (const block of blocks) {
    if (block.type === 'text' && block.text) parts.push(block.text.trim());
    if (block.type === 'tool') parts.push(`> 🔧 ${block.name}${block.status ? `（${block.status}）` : ''}`);
  }
  return `### 助手 · ${formatTime(message.ts)}\n\n${parts.join('\n\n') || '（无内容）'}`;
}

const rows = [];
let titled = 0;
let exported = 0;

if (exportDir) fs.mkdirSync(exportDir, { recursive: true });

for (const conversation of conversations) {
  const sessionId = conversation.sessionId;
  if (!sessionId) continue;
  const title = String(overrides[sessionId] || conversation.title || '').trim();
  if (!title) continue;

  const ok = await syncSessionTitle(sessionId, title);
  if (ok) titled += 1;

  const sessionDir = findSessionDir(sessionId);
  const transcript = sessionDir ? path.join(sessionDir, `${sessionId}.jsonl`) : null;

  if (exportDir) {
    const body = [
      `# ${title}`,
      '',
      `- 会话 ID：${sessionId}`,
      `- 工作目录：${conversation.cwd}`,
      `- 模型：${conversation.model}`,
      `- 最后更新：${formatTime(conversation.updatedAt)}`,
      `- 桌面端续接：claude --resume ${sessionId}`,
      '',
      '---',
      '',
      ...conversation.messages.filter((m) => !m.hidden).map(messageToMarkdown),
      '',
    ].join('\n');
    const file = path.join(exportDir, `${formatTime(conversation.updatedAt).replace(/[-: ]/g, '-')}-${sessionId.slice(0, 8)}.md`);
    fs.writeFileSync(file, body, 'utf8');
    exported += 1;
  }

  rows.push({
    time: formatTime(conversation.updatedAt),
    title,
    sessionId,
    titled: ok,
    transcript: transcript && fs.existsSync(transcript),
  });
}

rows.sort((a, b) => (a.time < b.time ? 1 : -1));

console.log('');
console.log(`手机端对话共 ${conversations.length} 个，其中有 CLI 会话 ID 的 ${rows.length} 个`);
console.log(`已写入桌面端标题 ${titled} 个${exportDir ? `，已导出 Markdown ${exported} 份到 ${exportDir}` : ''}`);
console.log('');
console.log('时间'.padEnd(12) + '标题'.padEnd(30) + '会话 ID');
console.log('-'.repeat(90));
for (const row of rows) {
  console.log(
    row.time.padEnd(14) +
      row.title.slice(0, 16).padEnd(20) +
      row.sessionId +
      (row.titled ? '' : '（标题没写进去：找不到会话目录）'),
  );
}
console.log('');
console.log('在桌面端续接任意一个：claude --resume <会话 ID>');
console.log('');
