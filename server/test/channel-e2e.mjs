/**
 * 通道切换端到端自测：真正起两个 CLI 会话，分别打 OpenCode 与 DeepSeek 官方。
 * 只发一句极短的话，验证「凭据可用 + 切通道后能 resume 继续」。
 * 用法：node test/channel-e2e.mjs
 */
import fs from 'node:fs';

const cfg = JSON.parse(fs.readFileSync(new URL('../config.json', import.meta.url), 'utf8'));
const base = `http://127.0.0.1:${cfg.port || 8787}`;
const headers = { Authorization: `Bearer ${cfg.token}`, 'Content-Type': 'application/json' };

const api = async (path, init = {}) => {
  const res = await fetch(base + path, { headers, ...init });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
};

const textOf = (m) =>
  String(m.text || '')
  || (Array.isArray(m.blocks) ? m.blocks.filter((b) => b.type === 'text').map((b) => b.text || '').join('') : '');

const waitForReply = async (id, since, timeoutMs = 45000) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const data = await api(`/api/conversation?id=${encodeURIComponent(id)}`);
    const msgs = (data.messages || []).slice(since);
    const assistant = msgs.filter((m) => m.role === 'assistant' && textOf(m).trim());
    if (assistant.length) return { ok: true, text: textOf(assistant.at(-1)).slice(0, 120) };
    await new Promise((r) => setTimeout(r, 1500));
  }
  return { ok: false, text: '超时未收到回复' };
};

const countMessages = async (id) => {
  const data = await api(`/api/conversation?id=${encodeURIComponent(id)}`);
  return (data.messages || []).length;
};

const run = async () => {
  const created = await api('/api/conversations', {
    method: 'POST',
    body: JSON.stringify({ channel: 'opencode', model: 'claude-haiku-5-5', cwd: cfg.defaultCwd }),
  });
  const conv = created.conversation || created;
  console.log('建会话:', conv.id, 'channel =', conv.channel, 'model =', conv.model);

  let since = await countMessages(conv.id);
  await api('/api/send', {
    method: 'POST',
    body: JSON.stringify({
      conversationId: conv.id,
      id: `t-oc-${Date.now()}`,
      text: '只回复 OK 两个字母，不要其他内容。',
      channel: 'opencode',
      model: 'claude-haiku-5-5',
    }),
  });
  const a = await waitForReply(conv.id, since);
  console.log('OpenCode 通道 →', a.ok ? '成功' : '失败', '|', a.text);

  since = await countMessages(conv.id);
  await api('/api/send', {
    method: 'POST',
    body: JSON.stringify({
      conversationId: conv.id,
      id: `t-ds-${Date.now()}`,
      text: '只回复 OK2 三个字符，不要其他内容。',
      channel: 'deepseek',
      model: 'deepseek-flash',
    }),
  });
  const b = await waitForReply(conv.id, since);
  console.log('切到 DeepSeek 后 →', b.ok ? '成功' : '失败', '|', b.text);

  const after = await api(`/api/conversation?id=${encodeURIComponent(conv.id)}`);
  const meta = after.meta || after.conversation || after;
  console.log('会话收尾: channel =', meta.channel, 'model =', meta.model);
  console.log(a.ok && b.ok ? 'E2E PASS' : 'E2E FAIL');
  process.exit(a.ok && b.ok ? 0 : 1);
};

run().catch((e) => {
  console.error('ERR', e.message);
  process.exit(1);
});
