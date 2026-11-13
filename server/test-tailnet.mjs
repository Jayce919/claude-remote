/**
 * 走 tailnet HTTPS 端到端测试：真发一条消息，看模型回复。
 *
 * 如果电脑的 DNS 被代理软件的 fake-IP 模式劫持（ts.net 会解析成 198.18.x），
 * 这里把 dns.lookup 打个补丁钉到真实 tailnet IP，模拟手机端的解析结果。
 * 手机上的 MagicDNS 会自己解对，不需要这个补丁。
 */
import dns from 'node:dns';

const HOST = 'YOUR-HOST.YOUR-TAILNET.ts.net';
const TAILNET_IP = '100.64.0.1';
const origLookup = dns.lookup;
dns.lookup = function patched(hostname, opts, cb) {
  if (hostname === HOST) {
    const options = typeof opts === 'object' && opts ? opts : {};
    const done = typeof opts === 'function' ? opts : cb;
    if (options.all) return done(null, [{ address: TAILNET_IP, family: 4 }]);
    return done(null, TAILNET_IP, 4);
  }
  return origLookup.call(dns, hostname, opts, cb);
};
const origPromiseLookup = dns.promises.lookup.bind(dns.promises);
dns.promises.lookup = async function patchedAsync(hostname, opts) {
  if (hostname === HOST) {
    const options = typeof opts === 'object' && opts ? opts : {};
    if (options.all) return [{ address: TAILNET_IP, family: 4 }];
    return { address: TAILNET_IP, family: 4 };
  }
  return origPromiseLookup(hostname, opts);
};

const BASE = process.argv[2] || `https://${HOST}`;
const TOKEN = process.argv[3];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const login = await fetch(`${BASE}/api/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: TOKEN }),
  });
  if (!login.ok) throw new Error(`登录失败 HTTP ${login.status}`);
  const cookie = (login.headers.get('set-cookie') || '').split(';')[0];
  console.log(`登录成功（${BASE}）`);
  console.log(`cookie: ${cookie.slice(0, 20)}…`);

  const H = { 'Content-Type': 'application/json', Cookie: cookie };
  const boot = await fetch(`${BASE}/api/bootstrap`, { headers: H }).then((r) => r.json());
  console.log(`历史对话 ${boot.conversations.length} 个，上游 ${boot.upstream}`);

  const created = await fetch(`${BASE}/api/conversations`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({ cwd: (process.env.USERPROFILE || process.cwd()).replace(/\\/g, '/') }),
  }).then((r) => r.json());
  const convId = created.conversation.id;

  const events = [];
  const res = await fetch(`${BASE}/api/events`, { headers: { Cookie: cookie } });
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  (async () => {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf('\n\n')) !== -1) {
        const chunk = buf.slice(0, i);
        buf = buf.slice(i + 2);
        const line = chunk.split('\n').find((l) => l.startsWith('data: '));
        if (line) events.push(JSON.parse(line.slice(6)));
      }
    }
  })();

  await sleep(500);
  await fetch(`${BASE}/api/send`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      conversationId: convId,
      text: '用一句话说明你正在被手机远程调用',
    }),
  });

  for (let i = 0; i < 120; i += 1) {
    await sleep(500);
    if (events.some((e) => e.t === 'result' && e.convId === convId)) break;
  }

  const result = events.find((e) => e.t === 'result' && e.convId === convId);
  const deltas = events.filter((e) => e.t === 'delta').length;
  console.log(`流式增量 ${deltas} 个`);
  console.log('模型回复:', String(result?.result || '(无)').slice(0, 220));
  reader.cancel().catch(() => {});
  process.exit(0);
}

main().catch((err) => {
  console.error('失败:', err.message);
  process.exit(1);
});
