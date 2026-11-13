import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 只读冒烟：连正在跑的服务，验证鉴权 + 两个核心接口。不会启动或重启任何进程。
const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const config = JSON.parse(fs.readFileSync(path.join(root, 'config.json'), 'utf8'));
const base = `http://${config.host}:${config.port}`;

async function get(pathname) {
  const res = await fetch(base + pathname, {
    headers: { authorization: `Bearer ${config.token}` },
  });
  return { status: res.status, ok: res.ok, text: await res.text() };
}

try {
  const bootstrap = await get('/api/bootstrap');
  if (!bootstrap.ok) {
    console.error(`bootstrap 失败：HTTP ${bootstrap.status}\n${bootstrap.text.slice(0, 500)}`);
    process.exit(1);
  }
  const data = JSON.parse(bootstrap.text);
  console.log(`bootstrap ok：models=${(data.models || []).length} provider=${data.provider || ''}`);

  const usage = await get('/api/usage');
  console.log(`usage ${usage.ok ? 'ok' : `HTTP ${usage.status}`}`);
  process.exit(usage.ok ? 0 : 1);
} catch (err) {
  console.error(`连不上服务：${err?.cause?.code || err?.message || err}`);
  console.error('服务没在跑？用 重启服务.cmd，或便携 Node 26 跑 server.js');
  process.exit(1);
}
