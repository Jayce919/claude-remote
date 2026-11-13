import fs from 'node:fs';

const BASE = 'http://127.0.0.1:8787';
const TOKEN = process.argv[2];
const cookie = `cm_auth=${encodeURIComponent(TOKEN)}`;
const H = { 'Content-Type': 'application/json', Cookie: cookie };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const events = [];

async function main() {
  const res = await fetch(`${BASE}/api/events`, { headers: { Cookie: cookie } });
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  (async () => {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let i;
      while ((i = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, i);
        buffer = buffer.slice(i + 2);
        const line = chunk.split('\n').find((l) => l.startsWith('data: '));
        if (line) events.push(JSON.parse(line.slice(6)));
      }
    }
  })();

  await sleep(400);
  const created = await fetch(`${BASE}/api/conversations`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({ cwd: (process.env.USERPROFILE || process.cwd()).replace(/\\/g, '/'), model: 'deepseek-v4-flash-vision-exp' }),
  }).then((r) => r.json());
  const convId = created.conversation.id;

  const png = fs.readFileSync('test.png');
  const base64 = png.toString('base64');

  console.log('1) 上传文件');
  const uploaded = await fetch(`${BASE}/api/upload`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({ name: 'test.png', data: base64, mediaType: 'image/png' }),
  }).then((r) => r.json());
  console.log('   ->', uploaded.path);

  console.log('2) 带图片发消息');
  await fetch(`${BASE}/api/send`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      conversationId: convId,
      text: '这张图里是深色和浅色相间的方格，请只回答：浅色方格一共有几行？',
      images: [{ name: 'test.png', mediaType: 'image/png', data: base64 }],
      model: 'deepseek-v4-flash-vision-exp',
    }),
  });

  for (let i = 0; i < 120; i += 1) {
    await sleep(500);
    if (events.some((e) => e.t === 'result' && e.convId === convId)) break;
  }

  const result = events.find((e) => e.t === 'result' && e.convId === convId);
  console.log('\n模型回答:', result?.result?.slice(0, 300));
  console.log('模型:', events.find((e) => e.t === 'session' && e.convId === convId)?.model);
  reader.cancel().catch(() => {});
  process.exit(0);
}

main().catch((err) => {
  console.error('失败:', err);
  process.exit(1);
});
