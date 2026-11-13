import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { renameSession } from '@anthropic-ai/claude-agent-sdk';

const PROJECTS_DIR = path.join(os.homedir(), '.claude', 'projects');

/**
 * 找出某个 CLI 会话落在 ~/.claude/projects 的哪个目录。
 * 目录名是工作目录的编码（Windows 上是 C:\Users\... ，命令行里又可能是 C:/Users/...），
 * 两种写法编码结果不同，所以不自己算，直接按会话的 jsonl 文件名找。
 */
export function findSessionDir(sessionId) {
  if (!sessionId) return null;
  let names;
  try {
    names = fs.readdirSync(PROJECTS_DIR);
  } catch {
    return null;
  }
  for (const name of names) {
    const dir = path.join(PROJECTS_DIR, name);
    if (fs.existsSync(path.join(dir, `${sessionId}.jsonl`))) return dir;
  }
  return null;
}

/**
 * 把标题同步给桌面端。
 *
 * 手机端和桌面端共用同一套 CLI 会话文件，但会话选择器读的是记录文件里的
 * custom-title 记录，没有这条记录就只显示第一句原文。这里用 SDK 官方的
 * renameSession 去写，不自己往会话记录里塞行；桌面端 App 的索引另外补一份
 * custom-title.json。任何一步失败都只记日志，不影响手机端对话本身。
 */
export async function syncSessionTitle(sessionId, title) {
  const clean = String(title || '').trim();
  if (!sessionId || !clean) return false;

  const projectDir = findSessionDir(sessionId);
  if (projectDir) {
    try {
      const sessionDir = path.join(projectDir, sessionId);
      fs.mkdirSync(sessionDir, { recursive: true });
      fs.writeFileSync(path.join(sessionDir, 'custom-title.json'), JSON.stringify({ customTitle: clean }));
    } catch {
      /* 桌面端索引写不进去不影响会话本身 */
    }
  }

  try {
    await renameSession(sessionId, clean);
    return true;
  } catch (err) {
    if (process.env.CM_DEBUG) console.error('[desktop-sync] 标题写入失败：', err?.message || err);
    return false;
  }
}
