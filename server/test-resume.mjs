const BASE = 'http://127.0.0.1:8787';
const TOKEN = process.argv[2];
const cookie = `cm_auth=${encodeURIComponent(TOKEN)}`;
const H = { 'Content-Type': 'application/json', Cookie: cookie };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const boot = await fetch(`${BASE}/api/bootstrap`, { headers: H }).then((r) => r.json());
  const convs = boot.conversations;
  console.log(`磁盘上有 ${convs.length} 个对话`);
  if (!convs.length) {
    console.log('没有可续的对话');
    return;
  }
  const conv = convs[0];
  console.log(`选定: ${conv.title}`);
  console.log(`  sessionId = ${conv.sessionId}`);
  console.log(`  live = ${conv.live}  (重启后应为 false)`);

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

  await sleep(600);
  console.log('\n发送追问: 请用一句话复述我上一条问的公式是什么');
  await fetch(`${BASE}/api/send`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      conversationId: conv.id,
      text: '请用一句话复述我上一条问的公式是什么',
    }),
  });

  for (let i = 0; i < 120; i += 1) {
    await sleep(500);
    if (events.some((e) => e.t === 'result' && e.convId === conv.id)) break;
  }

  const sess = events.find((e) => e.t === 'session' && e.convId === conv.id);
  const result = events.find((e) => e.t === 'result' && e.convId === conv.id);
  console.log('\n新 session 事件:', sess ? sess.sessionId : '(无)');
  console.log('  与旧 sessionId 一致:', sess && sess.sessionId === conv.sessionId);
  console.log('回答:', String(result?.result || '(无)').slice(0, 300));
  reader.cancel().catch(() => {});
  process.exit(0);
}

main().catch((err) => {
  console.error('失败:', err);
  process.exit(1);
});
